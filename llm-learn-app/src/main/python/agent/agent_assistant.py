"""
Week5 Day7 - 单Agent智能助手（5工具）
Step 1: Agent主循环框架（ReAct范式）
"""

import json
from openai import OpenAI
import mysql.connector

# --- Agent主循环 ---
SILICONFLOW_API_KEY = "os.environ.get("SILICONFLOW_API_KEY", "")"
SILICONFLOW_BASE_URL = "https://api.siliconflow.cn/v1"
MODEL = "Pro/zai-org/GLM-5.1"

client = OpenAI(api_key=SILICONFLOW_API_KEY, base_url=SILICONFLOW_BASE_URL)

# ========== 工具注册表（暂时空，后面步骤逐个加） ==========
TOOL_FUNCTIONS = {}  # 工具名 → 函数
TOOL_SCHEMAS = []    # 工具的JSON Schema描述，喂给LLM

# ========== Step 6：权限控制 + 交互式模式 ==========

# 工具权限级别：
# - auto: 无需确认，直接执行（低风险：天气、计算器）
# - confirm: 需要用户确认才执行（中风险：数据库查询）
# - confirm_always: 每次都需确认（高风险：文件写入）
TOOL_PERMISSIONS = {
    "get_weather": "auto",
    "query_mysql": "confirm",
    "calculate": "auto",
    "read_file": "confirm",
    "write_file": "confirm_always",
}


def check_permission(func_name: str, func_args: dict) -> tuple[bool, str]:
    """检查工具权限，confirm级别需要用户确认
    返回 (是否允许执行, 工具执行结果或拒绝原因)
    """
    permission = TOOL_PERMISSIONS.get(func_name, "confirm")

    if permission == "auto":
        # 低风险工具，直接执行
        return True, TOOL_FUNCTIONS[func_name](**func_args)

    elif permission == "confirm":
        # 中风险，需用户确认
        print(f"\n⚠️ 工具 {func_name} 需要确认")
        print(f"   参数: {func_args}")
        choice = input("   是否执行？(y/n): ").strip().lower()
        if choice == "y":
            return True, TOOL_FUNCTIONS[func_name](**func_args)
        else:
            return False, f"用户拒绝执行工具 {func_name}"

    elif permission == "confirm_always":
        # 高风险，每次都需确认
        print(f"\n🔴 工具 {func_name} 涉及文件修改，必须确认！")
        print(f"   参数: {func_args}")
        choice = input("   是否执行？(y/n): ").strip().lower()
        if choice == "y":
            return True, TOOL_FUNCTIONS[func_name](**func_args)
        else:
            return False, f"用户拒绝执行工具 {func_name}"

    return False, f"未知权限级别: {permission}"


def agent_loop(user_query: str, max_iterations: int = 5) -> str:
    """
    ReAct主循环（带权限控制版）：
    1. 用户提问 -> LLM思考是否需要调工具
    2. 如果LLM返回tool_calls -> 检查权限 -> 执行 -> 喂回LLM
    3. 否则返回最终回答
    """
    system_prompt = "你是一个智能助手，可以使用工具帮助用户。你有天气查询、数据库查询、计算器、文件读取、文件写入等工具。\n\n重要规则：当用户的问题可以通过工具解决时，你必须调用工具，不要自己猜测答案。例如：查天气必须调get_weather，不要自己编造天气信息；查数据必须调query_mysql；算数学必须调calculate。只有工具无法解决的问题，才用自己的知识回答。"

    messages = [{"role": "system", "content": system_prompt}, {"role": "user", "content": user_query}]

    for i in range(max_iterations):
        print(f"\n--- 第{i+1}轮 ---")

        response = client.chat.completions.create(
            model=MODEL,
            messages=messages,
            tools=TOOL_SCHEMAS if TOOL_SCHEMAS else None,

        )

        msg = response.choices[0].message
        messages.append(msg)

        # ====== 优先走标准 tool_calls ======
        if msg.tool_calls:
            for tool_call in msg.tool_calls:
                func_name = tool_call.function.name
                func_args = json.loads(tool_call.function.arguments)
                func_args = fix_args(func_name, func_args)

                print(f"🔧 调用工具(标准): {func_name}")
                print(f"   参数: {func_args}")

                allowed, result = check_permission(func_name, func_args)
                if not allowed:
                    print(f"   ⛔ 已拒绝: {result}")
                print(f"   结果: {result}")

                messages.append({
                    "role": "tool",
                    "tool_call_id": tool_call.id,
                    "content": str(result),
                })

        # ====== 兜底：解析文本中的工具调用 ======
        elif msg.content and ("name" in msg.content and "arguments" in msg.content):
            extracted = extract_tool_calls_from_text(msg.content)
            if extracted:
                print("🔧 检测到文本中的工具调用(兜底解析)")
                for func_name, func_args in extracted:
                    func_args = fix_args(func_name, func_args)
                    print(f"   工具: {func_name}, 参数: {func_args}")

                    allowed, result = check_permission(func_name, func_args)
                    if not allowed:
                        print(f"   ⛔ 已拒绝: {result}")
                    print(f"   结果: {result}")

                    messages.append({
                        "role": "user",
                        "content": f"工具 {func_name} 的执行结果是: {result}\n请基于这个结果回答用户的问题。",
                    })
                continue  # 继续循环让LLM生成最终回答
            else:
                print(f"💬 最终回答: {msg.content}")
                return msg.content

        # ====== 没有工具调用，纯文本回答 ======
        else:
            print(f"💬 最终回答: {msg.content}")
            return msg.content

    return "⚠️ Agent循环超限，未得到最终回答"


def extract_tool_calls_from_text(text: str) -> list:
    """
    从LLM文本中提取工具调用JSON。
    有些模型不稳定，有时走标准tool_calls，有时把调用写在文本里。
    生产级Agent必须有兜底机制。
    返回: [(func_name, func_args_dict)] 列表
    """
    import re
    results = []

    # 匹配 {"name": "xxx", "arguments": {...}} 格式
    pattern = r'\{"name"\s*:\s*"(\w+)"\s*,\s*"arguments"\s*:\s*(\{.*?\})\}'
    matches = re.findall(pattern, text, re.DOTALL)
    for func_name, args_str in matches:
        try:
            func_args = json.loads(args_str)
            results.append((func_name, func_args))
        except json.JSONDecodeError:
            pass

    return results


def fix_args(func_name: str, func_args: dict) -> dict:
    """
    修正LLM用错的参数名。
    例如：LLM可能传 location 而不是 city，传 path 而不是 filepath。
    """
    aliases = {
        "get_weather": {"location": "city", "place": "city"},
        "read_file": {"path": "filepath", "file_path": "filepath", "filename": "filepath"},
        "write_file": {"path": "filepath", "file_path": "filepath", "filename": "filepath"},
        "query_mysql": {"query": "sql", "statement": "sql"},
    }
    mapping = aliases.get(func_name, {})
    fixed = {}
    for key, val in func_args.items():
        fixed_key = mapping.get(key, key)
        fixed[fixed_key] = val
    return fixed


def interactive_mode():
    """交互式命令行模式，像聊天一样使用Agent"""
    print("\n" + "=" * 50)
    print("🧬 Agent智能助手 - 交互式模式")
    print("=" * 50)
    print("可用工具: 天气查询 / MySQL查询 / 计算器 / 文件读取 / 文件写入")
    print("权限级别: auto(天气/计算) | confirm(DB/读文件) | confirm_always(写文件)")
    print("输入 'quit' 退出，输入 'help' 查看工具列表")
    print("=" * 50)

    while True:
        user_input = input("\n🧑 你: ").strip()

        if not user_input:
            continue
        if user_input.lower() == "quit":
            print("👋 再见！")
            break
        if user_input.lower() == "help":
            print("\n可用工具：")
            for name, perm in TOOL_PERMISSIONS.items():
                desc = TOOL_SCHEMAS[[s["function"]["name"] for s in TOOL_SCHEMAS].index(name)]["function"]["description"]
                print(f"  - {name} [{perm}]: {desc}")
            continue

        answer = agent_loop(user_input)
        print(f"\n🤖 Agent: {answer}")


# ========== 工具1：天气查询 ==========
def get_weather(city: str) -> str:
    """通过wttr.in查询城市天气"""
    import requests
    try:
        # wttr.in 的 JSON 格式接口
        url = f"https://wttr.in/{city}?format=j1"
        resp = requests.get(url, timeout=10)
        data = resp.json()
        
        # 提取关键信息
        current = data["current_condition"][0]
        temp_c = current["temp_C"]
        feels_like = current["FeelsLikeC"]
        humidity = current["humidity"]
        desc = current["weatherDesc"][0]["value"]
        wind_speed = current["windspeedKmph"]
        
        return (
            f"{city}当前天气: {desc}, "
            f"温度{temp_c}°C(体感{feels_like}°C), "
            f"湿度{humidity}%, 风速{wind_speed}km/h"
        )
    except Exception as e:
        return f"查询天气失败: {e}"

# 注册天气工具
TOOL_FUNCTIONS["get_weather"] = get_weather
TOOL_SCHEMAS.append({
    "type": "function",
    "function": {
        "name": "get_weather",
        "description": "查询指定城市的当前天气信息，包括温度、湿度、风速等。返回中文描述。",
        "parameters": {
            "type": "object",
            "properties": {
                "city": {
                    "type": "string",
                    "description": "城市名称，可以是中文(如'北京')或英文(如'Beijing')"
                }
            },
            "required": ["city"]
        }
    }
})


# ========== 工具2：MySQL查询 ==========
def query_mysql(sql: str) -> str:
    """
    安全查询MySQL数据库，只允许SELECT语句。
    数据库: llm_learn (学习项目数据库)
    """
    
    # 安全检查：只允许SELECT
    sql_stripped = sql.strip().upper()
    if not sql_stripped.startswith("SELECT"):
        return "❌ 安全限制：只允许SELECT查询，不允许修改数据"
    
    # 截断分号防止多语句注入
    sql = sql.strip().rstrip(";").strip()
    
    try:
        conn = mysql.connector.connect(
            host="localhost",
            port=3306,
            user="root",
            password=os.environ.get("DB_PASSWORD", ""),
            database="llm_learn",
            charset="utf8mb4"
        )
        with conn.cursor() as cursor:
            cursor.execute(sql)
            rows = cursor.fetchall()
            # 限制返回最多20行
            if len(rows) > 20:
                result = rows[:20]
                truncated = True
            else:
                result = rows
                truncated = False
        
        conn.close()
        
        if not result:
            return "查询结果为空，没有匹配的数据"
        
        # 格式化为易读的文本
        output = json.dumps(result, ensure_ascii=False, default=str)
        if truncated:
            output += f"\n（仅显示前20行，总共{len(rows)}行）"
        return output
    
    except Exception as e:
        # Docker可能没启动等情况
        if "Can't connect" in str(e) or "2003" in str(e):
            return "❌ 数据库连接失败：请确认Docker中的MySQL容器已启动（docker start llm-mysql）"
        return f"查询异常: {e}"

# 注册MySQL工具
TOOL_FUNCTIONS["query_mysql"] = query_mysql
TOOL_SCHEMAS.append({
    "type": "function",
    "function": {
        "name": "query_mysql",
        "description": "查询llm_learn学习数据库。只允许SELECT语句。数据库包含LLM学习相关的实验数据表。使用前请先用SHOW TABLES查看有哪些表。",
        "parameters": {
            "type": "object",
            "properties": {
                "sql": {
                    "type": "string",
                    "description": "SQL SELECT查询语句，如 'SHOW TABLES' 或 'SELECT * FROM table_name LIMIT 5'"
                }
            },
            "required": ["sql"]
        }
    }
})


# ========== 工具3：计算器 ==========
def calculate(expression: str) -> str:
    """安全计算数学表达式，只允许数字和基本运算符"""
    import re
    if not re.match(r'^[\d\s\.\+\-\*/\%\(\)]+$', expression.strip()):
        return "❌ 安全限制：只允许数字和基本运算符，不允许函数或变量"
    try:
        result = eval(expression, {"__builtins__": {}}, {})
        if isinstance(result, float):
            result = round(result, 6)
        return f"{expression} = {result}"
    except ZeroDivisionError:
        return "❌ 计算错误：除数不能为零"
    except Exception as e:
        return f"❌ 计算失败: {e}"

# 注册计算器工具
TOOL_FUNCTIONS["calculate"] = calculate
TOOL_SCHEMAS.append({
    "type": "function",
    "function": {
        "name": "calculate",
        "description": "计算数学表达式。支持加减乘除、取模、幂运算。输入纯数学表达式如 '(15+27)*3/2'，不要包含文字描述。",
        "parameters": {
            "type": "object",
            "properties": {
                "expression": {
                    "type": "string",
                    "description": "纯数学表达式，如 '(15+27)*3/2'"
                }
            },
            "required": ["expression"]
        }
    }
})

# ========== 工具4：文件读写 ==========
def read_file(filepath: str) -> str:
    """读取指定文件的内容，路径必须在白名单目录内"""
    import os

    # 白名单：只允许读取项目目录下的文件
    allowed_dirs = [
        "/Users/zhiweizhang/Downloads/aicc/workspace/llm-learn",
        "/tmp",
        "/private/tmp",  # macOS下/tmp实际指向/private/tmp
    ]
    # 防路径穿越：解析真实路径
    real_path = os.path.realpath(filepath)
    allowed = any(real_path.startswith(d) for d in allowed_dirs)
    if not allowed:
        return f"❌ 安全限制：只允许读取项目目录和/tmp下的文件，你的路径: {real_path}"

    try:
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()
        # 限制返回最多500行
        lines = content.split("\n")
        if len(lines) > 500:
            return "\n".join(lines[:500]) + f"\n...（总共{len(lines)}行，仅显示前500行）"
        return content
    except FileNotFoundError:
        return f"❌ 文件不存在: {filepath}"
    except Exception as e:
        return f"❌ 读取失败: {e}"


def write_file(filepath: str, content: str) -> str:
    """将内容写入指定文件，路径必须在白名单目录内"""
    import os

    allowed_dirs = [
        "/Users/zhiweizhang/Downloads/aicc/workspace/llm-learn",
        "/tmp",
        "/private/tmp",  # macOS下/tmp实际指向/private/tmp
    ]
    real_path = os.path.realpath(filepath)
    allowed = any(real_path.startswith(d) for d in allowed_dirs)
    if not allowed:
        return f"❌ 安全限制：只允许写入项目目录和/tmp下的文件"

    try:
        # 自动创建父目录
        os.makedirs(os.path.dirname(real_path), exist_ok=True)
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)
        return f"✅ 写入成功: {filepath}，共{len(content)}字符"
    except Exception as e:
        return f"❌ 写入失败: {e}"


# 注册文件读取工具
TOOL_FUNCTIONS["read_file"] = read_file
TOOL_SCHEMAS.append({
    "type": "function",
    "function": {
        "name": "read_file",
        "description": "读取文件内容。只允许读取项目目录(llm-learn)和/tmp下的文件。",
        "parameters": {
            "type": "object",
            "properties": {
                "filepath": {
                    "type": "string",
                    "description": "文件的绝对路径，如 '/tmp/test.txt'"
                }
            },
            "required": ["filepath"]
        }
    }
})

# 注册文件写入工具
TOOL_FUNCTIONS["write_file"] = write_file
TOOL_SCHEMAS.append({
    "type": "function",
    "function": {
        "name": "write_file",
        "description": "将内容写入文件。只允许写入项目目录(llm-learn)和/tmp下。会自动创建父目录。",
        "parameters": {
            "type": "object",
            "properties": {
                "filepath": {
                    "type": "string",
                    "description": "文件的绝对路径，如 '/tmp/notes.txt'"
                },
                "content": {
                    "type": "string",
                    "description": "要写入的文件内容"
                }
            },
            "required": ["filepath", "content"]
        }
    }
})




def interactive_mode():
    """交互式命令行模式，像聊天一样使用Agent"""
    print("\n" + "=" * 50)
    print("🧬 Agent智能助手 - 交互式模式")
    print("=" * 50)
    print("可用工具: 天气查询 / MySQL查询 / 计算器 / 文件读取 / 文件写入")
    print("权限级别: auto(天气/计算) | confirm(DB/读文件) | confirm_always(写文件)")
    print("输入 'quit' 退出，输入 'help' 查看工具列表")
    print("=" * 50)

    while True:
        user_input = input("\n🧑 你: ").strip()

        if not user_input:
            continue
        if user_input.lower() == "quit":
            print("👋 再见！")
            break
        if user_input.lower() == "help":
            print("\n可用工具：")
            for name, perm in TOOL_PERMISSIONS.items():
                desc = TOOL_SCHEMAS[[s["function"]["name"] for s in TOOL_SCHEMAS].index(name)]["function"]["description"]
                print(f"  - {name} [{perm}]: {desc}")
            continue

        answer = agent_loop(user_input)
        print(f"\n🤖 Agent: {answer}")


if __name__ == "__main__":
    import sys



    if len(sys.argv) > 1 and sys.argv[1] == "--interactive":
        # 交互式模式
        interactive_mode()
    else:
        # 自动化测试模式（权限全部设为auto，不需要手动确认）
        # 临时覆盖权限，方便批量测试
        for name in TOOL_PERMISSIONS:
            TOOL_PERMISSIONS[name] = "auto"

        print("=" * 50)
        print("Agent智能助手 - Step 6 最终整合测试（自动模式）")
        print("=" * 50)

        # 测试1：纯对话（不调工具）
        print("\n[纯对话] 你好，请用一句话介绍你自己")
        answer1 = agent_loop("你好，请用一句话介绍你自己")
        print(f"✅ 回答: {answer1}")

        # 测试2：单工具
        print("\n[单工具] 北京今天天气怎么样？")
        answer2 = agent_loop("北京今天天气怎么样？")
        print(f"✅ 回答: {answer2}")

        # 测试3：双工具串联
        print("\n[串联] 上海和北京温度差是多少？")
        answer3 = agent_loop("上海和北京的温度差是多少？先查天气再算差值")
        print(f"✅ 回答: {answer3}")

        # 测试4：文件读写串联
        print("\n[文件串联] 把'Week5 Day7完成!'写入/tmp/summary.txt，然后读取确认")
        answer4 = agent_loop("把'Week5 Day7完成!'写入/tmp/summary.txt，然后读取确认内容是否正确")
        print(f"✅ 回答: {answer4}")

        print("\n" + "=" * 50)
        print("🎉 全部测试完成！现在可以用交互式模式体验：")
        print("   python agent_assistant.py --interactive")
        print("=" * 50)
