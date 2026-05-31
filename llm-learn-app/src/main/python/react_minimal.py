"""
ReAct 最小实现 — 纯手敲，不依赖任何框架
核心循环: Thought → Action → Observation → 重复直到Done
"""

import json
import re

# ---- 工具定义 ----

def search(query: str) -> str:
    """模拟搜索引擎，返回硬编码结果"""
    knowledge = {
        "北京到上海高铁票价": "北京到上海高铁二等座票价553元，一等座933元，商务座1748元",
        "北京到上海机票价格": "北京到上海机票经济舱800-1200元，取决于航班和时间",
        "高铁速度": "京沪高铁最高时速350km/h，全程约4小时18分钟",
        "飞机飞行时间": "北京到上海飞行时间约2小时15分钟",
        "京沪高铁里程": "京沪高铁全长1318公里",
    }
    results = []
    for key, value in knowledge.items():
        query_words = query.lower().split()
        if any(word in key.lower() for word in query_words):
            results.append(f"{key}: {value}")
    if results:
        return "\n".join(results)
    return f"未找到关于'{query}'的信息"


def calculate(expr: str) -> str:
    """简单数学计算，带安全检查"""
    allowed_pattern = r'^[\d\s\+\-\*/\.\(\)]+$'
    if not re.match(allowed_pattern, expr.strip()):
        return f"不安全的表达式: {expr}"
    try:
        result = eval(expr)
        return f"{expr} = {result}"
    except Exception as e:
        return f"计算错误: {e}"


def lookup(key: str) -> str:
    """从本地知识库查信息"""
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


# ---- LLM模拟 ----

def call_llm(prompt: str, mode: str = "auto") -> str:
    """
    硬编码模拟LLM，根据mode返回对应格式
    mode="thought" → 返回思考内容
    mode="action"  → 返回 Action: tool[args]
    mode="judge"   → 返回 Answer:xxx 或 "继续"
    mode="auto"    → 根据prompt最后一行指令自动判断
    """
    # 根据prompt末尾的指令确定mode
    if mode == "auto":
        if "任务是否完成" in prompt.split("\n")[-1]:
            mode = "judge"
        elif "选择一个动作" in prompt.split("\n")[-1]:
            mode = "action"
        else:
            mode = "thought"

    # 判断context中已有什么信息
    has_train = "553" in prompt
    has_flight = "800" in prompt and "1200" in prompt
    has_calc = "647" in prompt or "= 647" in prompt

    if mode == "thought":
        if not has_train:
            return "我需要先查高铁票价，这是第一步"
        elif has_train and not has_flight:
            return "高铁票价已知，现在需要查机票价格才能比较"
        elif has_train and has_flight and not has_calc:
            return "两种价格都有了，该计算差价"
        else:
            return "信息齐全，可以给出答案了"

    elif mode == "action":
        if not has_train:
            return "Action: search[北京到上海高铁票价]"
        elif has_train and not has_flight:
            return "Action: search[北京到上海机票价格]"
        elif has_train and has_flight and not has_calc:
            return "Action: calculate[1200 - 553]"
        else:
            return "Answer: 信息已足够"

    elif mode == "judge":
        if has_train and has_flight and has_calc:
            return "Answer: 北京到上海高铁二等座553元，飞机经济舱800-1200元。高铁比飞机便宜247-647元。高铁还省去候机时间，综合性价比更高。"
        elif has_train and has_flight:
            return "Answer: 北京到上海高铁553元，飞机800-1200元，高铁便宜247-647元。"
        else:
            return "继续"

    return "继续"


# ---- Action解析 ----

def parse_action(action_raw: str) -> tuple:
    """解析 Action: tool_name[args]，返回 (tool_name, args)"""
    match = re.match(r'Action:\s*(\w+)\[(.+?)\]', action_raw.strip())
    if match:
        return match.group(1), match.group(2)
    match2 = re.match(r'Action:\s*(\w+)\s*[,\s]\s*(.+)', action_raw.strip())
    if match2:
        return match2.group(1), match2.group(2).strip()
    return None, None


# ---- ReAct 核心循环 ----

def react_loop(question: str, max_steps: int = 5) -> dict:
    """
    ReAct主循环: Thought → Action → Observation → 判断完成
    返回: {"answer": str, "trace": list, "steps": int}
    """
    trace = []
    context = f"问题: {question}\n"

    for step in range(max_steps):
        print(f"\n{'='*40}")
        print(f"Step {step + 1}")
        print(f"{'='*40}")

        # 1. Thought
        thought = call_llm(context, mode="thought")
        trace.append({"step": step+1, "type": "thought", "content": thought})
        context += f"Thought: {thought}\n"
        print(f"💭 Thought: {thought}")

        # 2. Action
        action_raw = call_llm(context, mode="action")
        print(f"🎯 {action_raw}")

        # 如果action返回的是Answer，直接结束
        if action_raw.startswith("Answer:"):
            answer = action_raw.replace("Answer:", "").strip()
            trace.append({"step": step+1, "type": "answer", "content": answer})
            print(f"\n✅ Answer: {answer}")
            return {"answer": answer, "trace": trace, "steps": step+1}

        # 解析action
        tool_name, tool_args = parse_action(action_raw)
        if tool_name is None:
            trace.append({"step": step+1, "type": "error", "content": f"无法解析: {action_raw}"})
            context += f"Error: 无法解析动作\n"
            continue

        # 3. Observation — 执行工具
        if tool_name in TOOLS:
            observation = TOOLS[tool_name]["func"](tool_args)
        else:
            observation = f"工具 {tool_name} 不存在。可用工具: {list(TOOLS.keys())}"

        trace.append({"step": step+1, "type": "action", "tool": tool_name, "args": tool_args})
        trace.append({"step": step+1, "type": "observation", "content": observation})
        context += f"Action: {tool_name}[{tool_args}]\nObservation: {observation}\n"
        print(f"👁 Observation: {observation}")

        # 4. 判断完成
        done_check = call_llm(context, mode="judge")
        if done_check.startswith("Answer:"):
            answer = done_check.replace("Answer:", "").strip()
            trace.append({"step": step+1, "type": "answer", "content": answer})
            print(f"\n✅ Answer: {answer}")
            return {"answer": answer, "trace": trace, "steps": step+1}

        print(f"🔄 继续循环...")

    return {"answer": "达到最大步数限制", "trace": trace, "steps": max_steps}


# ---- 测试 ----

if __name__ == "__main__":
    result = react_loop("北京到上海高铁票价是多少？比飞机便宜多少？")
    print("\n" + "=" * 60)
    print("最终结果:")
    print(json.dumps(result, indent=2, ensure_ascii=False))