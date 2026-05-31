"""
Function Calling 最小Demo — Step by Step
使用 SiliconFlow API（兼容OpenAI格式）
"""

import json
from pydoc import cli
from openai import OpenAI

SILICONFLOW_API_KEY = "sk-kemgxaacaqdehipgoywavavmpaiazkocfjyixcvsnslmeycv"
SILICONFLOW_BASE_URL = "https://api.siliconflow.cn/v1"

client = OpenAI(
    api_key=SILICONFLOW_API_KEY,
    base_url=SILICONFLOW_BASE_URL
)

MODEL = "Pro/zai-org/GLM-5.1"

# ---- Step 2: 定义工具 ----
tools = [
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "查询指定城市的天气信息",
            "parameters": {
                "type": "object",
                "properties": {
                    "city": {
                        "type": "string",
                        "description": "城市名称，如'北京'、'上海'"
                    }
                },
                "required": ["city"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "calculate",
            "description": "执行数学计算，支持加减乘除",
            "parameters": {
                "type": "object",
                "properties": {
                    "expression": {
                        "type": "string",
                        "description": "数学表达式，如'2+3'、'10*5'"
                    }
                },
                "required": ["expression"]
            }
        }
    }
]

# ---- Step 3: 发请求，观察LLM的"调用意图" ----
# 测试1: 天气问题 —— LLM应该选择get_weather
response = client.chat.completions.create(
    model=MODEL,
    messages=[{"role": "user", "content": "北京今天天气怎么样？"}],
    tools=tools
)

print("=" * 50)
print("【测试1】用户问: 北京今天天气怎么样？")
print("=" * 50)

# 查看LLM的决策
message = response.choices[0].message

print(f"LLM是否决定调用工具: {message.tool_calls is not None}")
print(f"LLM直接回复的内容: {message.content}")  # 有tool_calls时通常是None

if message.tool_calls:
    for tc in message.tool_calls:
        print(f"  选择的函数: {tc.function.name}")
        print(f"  传入的参数: {tc.function.arguments}")
        print(f"  调用ID: {tc.id}")
        

# ---- Step 4: 执行函数 + 第二轮对话 ----
# 先定义真正的函数实现（模拟的）
def get_weather(city: str) -> str:
    """模拟天气查询"""
    weather_data = {
        "北京": "25°C，晴天，空气质量良",
        "上海": "28°C，多云，东南风3级",
        "深圳": "32°C，雷阵雨，湿度85%",
    }
    return weather_data.get(city, f"{city}：暂无天气数据")

def calculate(expression: str) -> str:
    """安全计算数学表达式"""
    try:
        # 只允许数字和运算符，防注入
        allowed = set("0123456789+-*/.() ")
        if not all(c in allowed for c in expression):
            return "错误：表达式包含非法字符"
        result = eval(expression)
        return str(result)
    except Exception as e:
        return f"计算错误：{e}"

# ---- 完整的两轮对话流程 ----
def chat_with_tools(user_message: str):
    """完整的FC两轮对话"""
    # ===== 第1轮：用户提问 → LLM返回调用意图 =====
    print(f"\n{'='*50}")
    print(f"用户: {user_message}")
    print(f"{'='*50}")
    response = client.chat.completions.create(
        model=MODEL,
        messages=[
            {"role": "user", "content": user_message}
        ],
        tools=tools
    )
    
    message = response.choices[0].message
    # 情况A：LLM不需要工具，直接回答
    if not message.tool_calls:
        print(f"LLM直接回复: {message.content}")
        return message.content
    # 情况B：LLM决定调用工具
    # 把LLM的"意图消息"加入对话历史
    messages = [
        {"role": "user", "content": user_message},
        message  # LLM的tool_calls消息，原样保留
    ]
    
    # 逐个执行LLM要求的工具调用
    for tc in message.tool_calls:
        func_name = tc.function.name
        func_args = json.loads(tc.function.arguments)  # JSON字符串 → dict
        print(f"  🔧 LLM决定调用: {func_name}({func_args})")
        # 真正执行函数
        if func_name == "get_weather":
            result = get_weather(**func_args)
        elif func_name == "calculate":
            result = calculate(**func_args)
        else:
            result = f"未知函数: {func_name}"
            
        print(f"  📋 执行结果: {result}")
        # 把结果喂回对话 —— 格式必须是 tool message
        messages.append({
            "role": "tool",
            "tool_call_id": tc.id,  # 必须绑定到对应的调用ID
            "content": result
        })

    # ===== 第2轮：把工具结果喂回去 → LLM生成最终回答 =====
    response2 = client.chat.completions.create(
        model=MODEL,
        messages=messages,
        tools=tools
    )
    
    final_answer = response2.choices[0].message.content
    print(f"  ✅ 最终回答: {final_answer}")
    return final_answer
    
# ---- 测试三种场景 ----

# 场景1: 需要天气工具
chat_with_tools("北京今天天气怎么样？")

# 场景2: 需要计算工具
chat_with_tools("帮我算一下 123 乘以 456 等于多少")

# 场景3: 不需要任何工具
chat_with_tools("你好，请给我讲个冷笑话")

# ---- Step 5: 多工具调用测试 ----
chat_with_tools("帮我查一下上海天气，再算一下25加17等于多少")