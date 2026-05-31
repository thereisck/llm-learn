"""
ReAct — 接入硅基流动真实LLM版本
使用SiliconFlow API（兼容OpenAI格式）
配置来源: TOOLS.md → SILICONFLOW_BASE_URL + SILICONFLOW_API_KEY

使用前:
1. pip install openai
2. 确保环境变量已设置（或直接在代码里写，如下）
"""

import json
import re
import os
from openai import OpenAI

# ---- 硅基流动 LLM 配置 ----

SILICONFLOW_API_KEY = os.getenv("SILICONFLOW_API_KEY", "sk-kemgxaacaqdehipgoywavavmpaiazkocfjyixcvsnslmeycv")
SILICONFLOW_BASE_URL = os.getenv("SILICONFLOW_BASE_URL", "https://api.siliconflow.cn/v1")

client = OpenAI(
    api_key=SILICONFLOW_API_KEY,
    base_url=SILICONFLOW_BASE_URL
)

# 可选模型列表（硅基流动支持的对话模型）:
# - Qwen/Qwen2.5-7B-Instruct (免费，适合测试)
# - Qwen/Qwen2.5-72B-Instruct (更强)
# - deepseek-ai/DeepSeek-V2.5 (推理强)
# - THUDM/glm-4-9b-chat (清华)
MODEL = "Qwen/Qwen2.5-7B-Instruct"


# ---- 工具定义 ----

def search(query: str) -> str:
    """模拟搜索引擎"""
    knowledge = {
        "北京到上海高铁票价": "北京到上海高铁二等座票价553元，一等座933元，商务座1748元",
        "北京到上海机票价格": "北京到上海机票经济舱800-1200元，取决于航班和时间",
        "高铁速度": "京沪高铁最高时速350km/h，全程约4小时18分钟",
        "飞机飞行时间": "北京到上海飞行时间约2小时15分钟",
        "京沪高铁里程": "京沪高铁全长1318公里",
    }
    results = []
    for key, value in knowledge.items():
        if any(word in key.lower() for word in query.lower().split()):
            results.append(f"{key}: {value}")
    return "\n".join(results) if results else f"未找到关于'{query}'的信息"


def calculate(expr: str) -> str:
    """数学计算"""
    if not re.match(r'^[\d\s\+\-\*/\.\(\)]+$', expr.strip()):
        return f"不安全的表达式: {expr}"
    try:
        return f"{expr} = {eval(expr)}"
    except Exception as e:
        return f"计算错误: {e}"


def lookup(key: str) -> str:
    """本地知识库"""
    local_db = {
        "北京上海距离": "1318公里（高铁里程）",
        "高铁vs飞机时间": "高铁4小时18分 vs 飞机2小时15分（不含候机安检）",
        "高铁优势": "准时率高、市中心到市中心、不受天气影响",
        "飞机优势": "速度快（含候机约4-5小时总耗时）、选择多",
    }
    if key in local_db:
        return local_db[key]
    for k, v in local_db.items():
        if any(word in k for word in key.split()):
            return f"{k}: {v}"
    return f"知识库中未找到: {key}"


TOOLS = {
    "search": {"func": search, "desc": "搜索互联网信息，输入搜索关键词"},
    "calculate": {"func": calculate, "desc": "计算数学表达式，如 '1200 - 553'"},
    "lookup": {"func": lookup, "desc": "从本地知识库查询，输入关键词"},
}


# ---- 真实LLM调用 ----

def call_llm(prompt: str) -> str:
    """调用硅基流动API（兼容OpenAI格式）"""
    tool_descriptions = "\n".join(
        [f"- {name}: {info['desc']}" for name, info in TOOLS.items()]
    )

    system_prompt = f"""你是一个ReAct Agent，需要通过思考和调用工具来回答问题。

可用工具：
{tool_descriptions}

你必须严格按以下格式输出，每次只输出一步：

Thought: 你的思考过程
Action: tool_name[参数]

当任务完成时，改为：
Thought: 我已经收集了足够的信息
Answer: 最终答案

重要：每一步只输出一个Thought和一个Action/Answer，不要输出多步。"""

    try:
        response = client.chat.completions.create(
            model=MODEL,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": prompt}
            ],
            temperature=0.1,
            max_tokens=300,
        )
        return response.choices[0].message.content.strip()
    except Exception as e:
        return f"LLM调用失败: {e}"


# ---- Action解析 ----

def parse_action(action_raw: str) -> tuple:
    """解析 Action: tool_name[args]"""
    match = re.search(r'Action:\s*(\w+)\[(.+?)\]', action_raw)
    if match:
        return match.group(1), match.group(2)
    match2 = re.search(r'Action:\s*(\w+)\s*[,\s]\s*(.+)', action_raw)
    if match2:
        return match2.group(1), match2.group(2).strip()
    return None, None


# ---- ReAct 核心循环 ----

def react_loop(question: str, max_steps: int = 5) -> dict:
    """
    ReAct主循环：Thought → Action → Observation → 判断完成
    """
    trace = []
    context = f"问题: {question}\n"

    for step in range(max_steps):
        print(f"\n{'='*40}")
        print(f"Step {step + 1}")
        print(f"{'='*40}")

        # 1. 调用LLM
        llm_response = call_llm(context)
        print(f"🤖 LLM Response:\n{llm_response}")

        # 2. 提取Thought
        thought_match = re.search(r'Thought:\s*(.+?)(?:\n|$)', llm_response)
        thought = thought_match.group(1).strip() if thought_match else llm_response
        trace.append({"step": step+1, "type": "thought", "content": thought})
        context += f"Thought: {thought}\n"
        print(f"💭 Thought: {thought}")

        # 3. 检查是否直接给出了Answer
        answer_match = re.search(r'Answer:\s*(.+)', llm_response, re.DOTALL)
        if answer_match:
            answer = answer_match.group(1).strip()
            trace.append({"step": step+1, "type": "answer", "content": answer})
            print(f"\n✅ Answer: {answer}")
            return {"answer": answer, "trace": trace, "steps": step+1}

        # 4. 提取Action
        tool_name, tool_args = parse_action(llm_response)
        if tool_name is None:
            trace.append({"step": step+1, "type": "error", "content": f"无法解析: {llm_response}"})
            context += f"Error: 无法解析动作，请重新思考\n"
            continue

        # 5. 执行工具 → Observation
        if tool_name in TOOLS:
            observation = TOOLS[tool_name]["func"](tool_args)
        else:
            observation = f"工具 {tool_name} 不存在。可用: {list(TOOLS.keys())}"

        trace.append({"step": step+1, "type": "action", "tool": tool_name, "args": tool_args})
        trace.append({"step": step+1, "type": "observation", "content": observation})
        context += f"Action: {tool_name}[{tool_args}]\nObservation: {observation}\n"
        print(f"🎯 Action: {tool_name}[{tool_args}]")
        print(f"👁 Observation: {observation}")

    return {"answer": "达到最大步数限制", "trace": trace, "steps": max_steps}


# ---- 测试 ----

if __name__ == "__main__":
    print(f"🔧 使用模型: {MODEL}")
    print(f"🔧 API: {SILICONFLOW_BASE_URL}")

    result = react_loop("北京到上海高铁票价是多少？比飞机便宜多少？")
    print("\n" + "=" * 60)
    print("最终结果:")
    print(json.dumps(result, indent=2, ensure_ascii=False))