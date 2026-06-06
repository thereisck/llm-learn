"""Secure Agent：3工具 + 权限控制 + 人类确认 + 输入校验 + 重试"""
import json
import re
import os
import requests
import mysql.connector
from openai import OpenAI

# --- 权限等级定义 ---
# auto: 自动执行，无需确认
# confirm: 需人类确认（打印意图，等Y/N）
# confirm+whitelist: 需确认 + 路径白名单校验
TOOL_PERMISSIONS = {
    "get_weather": "auto",
    "query_database": "confirm",
    "read_file": "confirm+whitelist",
}

# --- 路径白名单 ---
ALLOWED_PATHS = ["/tmp", "/Users/zhiweizhang/Downloads"]
# --- 人类确认函数 ---
def human_confirm(tool_name: str, args: dict) -> bool:
    """
    对confirm级别的工具，打印意图让用户确认
    返回True=允许执行，False=拒绝
    """
    print(f"\n⚠️ Agent想执行: {tool_name}({args})")
    choice = input("允许执行？(Y=允许/N=拒绝): ").strip().upper()
    return choice == "Y"

# --- 工具1：天气查询（auto权限） ---
def get_weather(city: str) -> str:
    url = f"https://wttr.in/{city}?format=j1"
    resp = requests.get(url, timeout=10)
    resp.raise_for_status()
    data = resp.json()
    current = data["current_condition"][0]
    temp = current["temp_C"]
    desc = current["weatherDesc"][0]["value"]
    humidity = current["humidity"]
    return f"{city}当前天气：{desc}，温度{temp}°C，湿度{humidity}%"

# --- 工具2：数据库查询（confirm权限，自带SQL校验） ---
def query_database(sql: str) -> str:
    # 安全校验1：只允许SELECT
    if not re.match(r'^\s*SELECT\s', sql, re.IGNORECASE):
        return "错误：只允许SELECT查询，禁止INSERT/UPDATE/DELETE/DROP"
    # 安全校验2：截断分号防注入
    sql = sql.split(';')[0].strip()
    try:
        conn = mysql.connector.connect(
            host="localhost", port=3306,
            user="root", password=os.environ.get("DB_PASSWORD", ""),
            database="llm_learn", charset="utf8mb4"
        )
        cursor = conn.cursor(dictionary=True)
        cursor.execute(sql)
        rows = cursor.fetchall()
        cursor.close()
        conn.close()
        if not rows:
            return "查询结果为空"
        return json.dumps(rows, ensure_ascii=False, default=str)
    except Exception as e:
        return f"查询出错：{e}"
    
# --- 工具3：文件读取（confirm+whitelist权限，路径校验） ---
def read_file(filepath: str) -> str:
    # 安全校验：路径必须在白名单内
    real_path = os.path.realpath(filepath)
    allowed = any(real_path.startswith(p) for p in ALLOWED_PATHS)
    if not allowed:
        return f"错误：路径{filepath}不在白名单内，只允许读取{ALLOWED_PATHS}下的文件"
    if not os.path.exists(real_path):
        return f"错误：文件{filepath}不存在"
    try:
        with open(real_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()[:500]
            return ''.join(lines)
    except Exception as e:
        return f"读取出错：{e}"
    
# --- Tool定义（给LLM看的描述） ---
tools = [
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "查询指定城市的当前天气信息，包括温度、天气描述和湿度",
            "parameters": {
                "type": "object",
                "properties": {
                    "city": {
                        "type": "string",
                        "description": "城市名称，如Beijing、Shanghai、New York"
                    }
                },
                "required": ["city"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "query_database",
            "description": "查询MySQL数据库llm_learn，执行SELECT语句。employees表字段：id, name(姓名), department(部门), salary(薪资), hire_date(入职日期)",
            "parameters": {
                "type": "object",
                "properties": {
                    "sql": {
                        "type": "string",
                        "description": "要执行的SELECT SQL语句"
                    }
                },
                "required": ["sql"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "read_file",
            "description": "读取指定文件的文本内容，只允许读取/tmp和Downloads下的文件",
            "parameters": {
                "type": "object",
                "properties": {
                    "filepath": {
                        "type": "string",
                        "description": "文件绝对路径，必须在白名单目录内"
                    }
                },
                "required": ["filepath"]
            }
        }
    }
]

# --- 工具执行映射 ---
tool_map = {
    "get_weather": get_weather,
    "query_database": query_database,
    "read_file": read_file,
}

# --- 权限拦截执行器 ---
def execute_tool_with_permission(func_name: str, func_args: dict) -> str:
    """
    核心逻辑：先查权限等级，再决定是否执行
    auto → 直接执行
    confirm → 打印意图，等人类确认
    confirm+whitelist → 确认 + 路径校验（路径校验已在read_file内部完成）
    """
    permission = TOOL_PERMISSIONS.get(func_name, "confirm")
    
    # auto权限：直接执行
    if permission == "auto":
        print(f"[自动执行] {func_name}({func_args})")
        return tool_map[func_name](**func_args)
    
    # confirm权限：需人类确认
    if permission in ("confirm", "confirm+whitelist"):
        approved = human_confirm(func_name, func_args)
        if not approved:
            return f"用户拒绝执行 {func_name}"
        print(f"[已确认] 执行 {func_name}({func_args})")
        return tool_map[func_name](**func_args)
    
    return f"未知权限等级: {permission}"

# --- Agent主循环 ---
SILICONFLOW_API_KEY = "os.environ.get("SILICONFLOW_API_KEY", "")"
SILICONFLOW_BASE_URL = "https://api.siliconflow.cn/v1"
MODEL = "Pro/zai-org/GLM-5.1"

client = OpenAI(api_key=SILICONFLOW_API_KEY, base_url=SILICONFLOW_BASE_URL)

def chat_secure(user_message: str) -> str:
    messages = [{"role": "user", "content": user_message}]
    
    # 第一轮：LLM决定是否调工具
    response = client.chat.completions.create(
        model=MODEL, messages=messages, tools=tools,
    )
    choice = response.choices[0]
    message = choice.message
    
    if not message.tool_calls:
        return message.content
    
    messages.append(message)
    
    for tool_call in message.tool_calls:
        func_name = tool_call.function.name
        func_args = json.loads(tool_call.function.arguments)
        
        # 关键变化：不再直接调tool_map，而是走权限拦截器
        result = execute_tool_with_permission(func_name, func_args)
        
        messages.append({
            "role": "tool",
            "tool_call_id": tool_call.id,
            "content": result,
        })
    
    # 第二轮：LLM拿到结果生成回答
    response2 = client.chat.completions.create(
        model=MODEL, messages=messages, tools=tools,
    )
    return response2.choices[0].message.content

# --- 重试策略：多模型降级 + 指数退避 ---
FALLBACK_MODELS = ["Pro/zai-org/GLM-5.1", "deepseek-ai/DeepSeek-V4-Pro", "Qwen/Qwen3.5-397B-A17B"]
MAX_RETRIES = 3

def call_llm_with_retry(messages, tools=None):
    """
    3层降级：主模型超时→切备用模型→再超时→指数退避重试
    """
    for attempt in range(MAX_RETRIES):
        for model in FALLBACK_MODELS:
            try:
                kwargs = {"model": model, "messages": messages}
                if tools:
                    kwargs["tools"] = tools
                return client.chat.completions.create(**kwargs)
            except Exception as e:
                print(f"[重试] {model} 调用失败: {e}，切换下一个模型...")
                continue
        # 所有模型都失败了，指数退避
        wait = 2 ** attempt
        print(f"[退避] 所有模型失败，等待{wait}秒后重试...")
        import time
        time.sleep(wait)
    
    raise Exception("所有模型重试失败，请检查API服务")

# --- 交互式测试 ---
if __name__ == "__main__":
    print("=" * 50)
    print("Secure Agent 交互测试")
    print("权限等级: weather=auto, database=confirm, read_file=confirm+whitelist")
    print("输入 q 退出")
    print("=" * 50)
    
    while True:
        user_input = input("\n你: ").strip()
        if user_input.lower() == 'q':
            break
        if not user_input:
            continue
        
        answer = chat_secure(user_input)
        print(f"\nAgent: {answer}")