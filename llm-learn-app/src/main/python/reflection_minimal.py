"""
Reflection 最小实现
核心逻辑: 在ReAct基础上加"自我评估"环节
执行完 → LLM评估答案质量 → 不满意就重新思考 → 改进方案
"""

import json
import re


# ---- 工具定义 ----

def search(query: str) -> str:
    knowledge = {
        "北京到上海高铁票价": "北京到上海高铁二等座票价553元，一等座933元",
        "北京到上海机票价格": "北京到上海机票经济舱800-1200元",
        "京沪高铁里程": "京沪高铁全长1318公里",
    }
    results = []
    for key, value in knowledge.items():
        if any(word in key.lower() for word in query.lower().split()):
            results.append(f"{key}: {value}")
    return "\n".join(results) if results else f"未找到: {query}"

def calculate(expr: str) -> str:
    if not re.match(r'^[\d\s\+\-\*/\.\(\)]+$', expr.strip()):
        return f"不安全: {expr}"
    try:
        return f"{expr} = {eval(expr)}"
    except Exception as e:
        return f"错误: {e}"


TOOLS = {"search": search, "calculate": calculate}


# ---- LLM模拟 ----

def call_llm(prompt: str, mode: str = "thought") -> str:
    """和ReAct一样的模拟LLM，根据mode和context状态返回对应内容"""
    has_train = "553" in prompt
    has_flight = "800" in prompt and "1200" in prompt
    has_calc = "647" in prompt or "= 647" in prompt

    if mode == "thought":
        if not has_train:
            return "第一步：查高铁票价"
        elif has_train and not has_flight:
            return "第二步：查机票价格"
        elif has_train and has_flight and not has_calc:
            return "第三步：计算差价"
        return "信息齐全"

    elif mode == "action":
        if not has_train:
            return "Action: search[北京到上海高铁票价]"
        elif has_train and not has_flight:
            return "Action: search[北京到上海机票价格]"
        elif has_train and has_flight and not has_calc:
            return "Action: calculate[1200 - 553]"
        return "Answer: 完成"

    elif mode == "judge":
        if has_train and has_flight and has_calc:
            return "Answer: 高铁553元，飞机800-1200元，便宜247-647元。"
        return "继续"

    return "继续"


def reflect_llm(question: str, answer: str) -> dict:
    """
    ⚡ Reflection的核心：LLM评估自己的答案质量
    返回: {"satisfied": bool, "critique": str, "suggestions": list}
    """
    # 硬编码模拟三种评估结果：
    # 第一轮：答案不够详细（触发反思）
    # 第二轮：答案足够好（通过）

    # 判断答案质量
    has_detail = "二等座" in answer or "一等座" in answer
    has_range = "247" in answer and "647" in answer
    has_context = "候机" in answer or "性价比" in answer

    if not has_detail:
        return {
            "satisfied": False,
            "critique": "答案太粗略，只给了一个价格，没有说明是二等座还是一等座，也没有给出飞机价格的范围解释",
            "suggestions": [
                "补充座位等级信息（二等座553 vs 一等座933）",
                "解释差价范围（247-647取决于航班时段）",
                "加入时间成本比较"
            ]
        }

    if not has_context:
        return {
            "satisfied": False,
            "critique": "答案只有价格数据，缺少实用性分析——读者可能还想知道时间成本",
            "suggestions": [
                "加入时间对比（高铁4h18m vs 飞机2h15m+候机）",
                "给出综合性价比结论"
            ]
        }

    return {
        "satisfied": True,
        "critique": "答案详细且实用，包含了价格、范围和时间成本分析",
        "suggestions": []
    }


# ---- Action解析 ----

def parse_action(action_raw: str) -> tuple:
    match = re.match(r'Action:\s*(\w+)\[(.+?)\]', action_raw.strip())
    if match:
        return match.group(1), match.group(2)
    return None, None


# ---- ReAct循环（和之前一样） ----

def react_loop(question: str, max_steps: int = 5) -> dict:
    """标准ReAct循环，获取初步答案"""
    trace = []
    context = f"问题: {question}\n"

    for step in range(max_steps):
        thought = call_llm(context, mode="thought")
        trace.append({"step": step+1, "type": "thought", "content": thought})
        context += f"Thought: {thought}\n"

        action_raw = call_llm(context, mode="action")
        if action_raw.startswith("Answer:"):
            return {"answer": action_raw.replace("Answer:", "").strip(), "trace": trace}

        tool_name, tool_args = parse_action(action_raw)
        if tool_name is None:
            context += f"Error: 无法解析\n"
            continue

        observation = TOOLS[tool_name](tool_args) if tool_name in TOOLS else f"工具不存在"
        trace.append({"step": step+1, "type": "action", "tool": tool_name, "args": tool_args})
        trace.append({"step": step+1, "type": "observation", "content": observation})
        context += f"Action: {tool_name}[{tool_args}]\nObservation: {observation}\n"

        done = call_llm(context, mode="judge")
        if done.startswith("Answer:"):
            return {"answer": done.replace("Answer:", "").strip(), "trace": trace}

    return {"answer": "超时", "trace": trace}


# ---- Reflection 核心：ReAct + 自评 + 改进 ----

def reflection_loop(question: str, max_reflections: int = 3) -> dict:
    """
    1. 用ReAct获取初步答案
    2. 用reflect_llm评估答案质量
    3. 不满意 → 带着suggestions重新ReAct
    4. 重复直到满意或达到最大反思次数
    """
    print(f"{'='*50}")
    print(f"🔄 Reflection Loop — 最多{max_reflections}轮反思")
    print(f"{'='*50}")

    all_attempts = []
    current_question = question

    for attempt in range(max_reflections):
        print(f"\n--- Attempt {attempt+1} ---")

        # 1. ReAct获取答案
        print(f"  🏃 Running ReAct...")
        react_result = react_loop(current_question)
        answer = react_result["answer"]
        print(f"  📝 初步答案: {answer}")

        # 2. Reflection评估
        print(f"  🔍 Reflecting on answer quality...")
        reflection = reflect_llm(question, answer)
        print(f"  💬 Critique: {reflection['critique']}")
        print(f"  ✅ Satisfied: {reflection['satisfied']}")

        all_attempts.append({
            "attempt": attempt+1,
            "answer": answer,
            "reflection": reflection
        })

        # 3. 如果满意，返回
        if reflection["satisfied"]:
            print(f"\n🎉 反思通过！最终答案：{answer}")
            return {
                "answer": answer,
                "attempts": all_attempts,
                "total_attempts": attempt+1,
                "final_satisfied": True
            }

        # 4. 不满意 → 改进问题/补充要求
        print(f"  🔄 不满意，带着suggestions重新执行...")
        suggestions_str = "; ".join(reflection["suggestions"])
        current_question = f"{question}（请额外关注：{suggestions_str}）"
        print(f"  ➡️ 改进后的问题: {current_question}")

    # 达到最大反思次数仍未满意
    best_answer = all_attempts[-1]["answer"]
    print(f"\n⚠️ 达到最大反思次数{max_reflections}，取最后一次答案")
    return {
        "answer": best_answer,
        "attempts": all_attempts,
        "total_attempts": max_reflections,
        "final_satisfied": False
    }


# ---- 测试 ----

if __name__ == "__main__":
    result = reflection_loop("北京到上海高铁票价是多少？比飞机便宜多少？")
    print("\n" + "=" * 60)
    print("最终结果:")
    print(json.dumps(result, indent=2, ensure_ascii=False))