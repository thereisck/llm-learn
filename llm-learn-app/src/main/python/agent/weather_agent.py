import requests
import json
from openai import OpenAI

SILICONFLOW_API_KEY = "os.environ.get("SILICONFLOW_API_KEY", "")"
SILICONFLOW_BASE_URL = "https://api.siliconflow.cn/v1"

client = OpenAI(
    api_key=SILICONFLOW_API_KEY,
    base_url=SILICONFLOW_BASE_URL
)

MODEL = "Pro/zai-org/GLM-5.1"

def get_weather(city: str) -> str:
    """查询指定城市的天气信息"""
    url = f"https://wttr.in/{city}?format=j1"
    resp = requests.get(url, timeout=10)
    resp.raise_for_status()
    data = resp.json()
    
    current = data["current_condition"][0]
    temp = current["temp_C"]
    desc = current["weatherDesc"][0]["value"]
    humidity = current["humidity"]
    
    return f"{city}当前天气：{desc}，温度{temp}°C，湿度{humidity}%"


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
    }
]

# --- Tool执行映射 ---
tool_map = {
    "get_weather": get_weather,
}

# --- Tool执行映射 ---
tool_map = {
    "get_weather": get_weather,
}

def chat_with_weather(user_message: str) -> str:
    messages = [{"role": "user", "content": user_message}]
    # 第一轮：LLM决定是否调工具
    response = client.chat.completions.create(
        model=MODEL,
        messages=messages,
        tools=tools,
    )
    
    choice = response.choices[0]
    message = choice.message
    
    # 没有tool_call → LLM直接回答了，不需要调工具
    if not message.tool_calls:
        return message.content
    
    # 有tool_call → 执行工具
    messages.append(message)  # 把LLM的回复（含tool_calls）加入历史
    
    for tool_call in message.tool_calls:
        func_name = tool_call.function.name
        func_args = json.loads(tool_call.function.arguments)
        
        print(f"[工具调用] {func_name}({func_args})")  # 观察LLM怎么决策的
        
        result = tool_map[func_name](**func_args)
        
        # 把工具结果喂回去（role=tool）
        messages.append({
            "role": "tool",
            "tool_call_id": tool_call.id,
            "content": result,
        })
        
    # 第二轮：LLM拿到工具结果，生成最终回答
    response2 = client.chat.completions.create(
        model=MODEL,
        messages=messages,
    )
    
    return response2.choices[0].message.content

# 测试API
if __name__ == "__main__":
    print(chat_with_weather("北京天气"))
