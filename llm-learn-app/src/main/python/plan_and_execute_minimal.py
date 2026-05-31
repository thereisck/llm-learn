"""
Plan-and-Execute 最小实现
核心逻辑: 先规划完整步骤列表 → 再逐步执行 → 每步把结果喂回
和ReAct的区别: ReAct边想边做，Plan-and-Execute先想再做
"""

import json
import re


# ---- 工具定义（和ReAct共用） ----

def search(query: str) -> str:
    knowledge = {
        "北京到上海高铁票价": "北京到上海高铁二等座票价553元，一等座933元",
        "北京到上海机票价格": "北京到上海机票经济舱800-1200元",
        "京沪高铁里程": "京沪高铁全长1318公里",
    }
    results = []
    for key, value in knowledge.items():
        query_words = query.lower().split()
        if any(word in key.lower() for word in query_words):
            results.append(f"{key}: {value}")
    return "\n".join(results) if results else f"未找到: {query}"

def calculate(expr: str) -> str:
    if not re.match(r'^[\d\s\+\-\*/\.\(\)]+$', expr.strip()):
        return f"不安全的表达式: {expr}"
    try:
        return f"{expr} = {eval(expr)}"
    except Exception as e:
        return f"计算错误: {e}"


TOOLS = {"search": search, "calculate": calculate}


# ---- LLM模拟 ----

def plan_llm(question: str) -> list:
    """
    第一步：让LLM生成完整计划
    返回步骤列表，每个步骤是 {"tool": "xxx", "args": "xxx", "desc": "说明"}
    """
    # 硬编码模拟：针对"高铁vs飞机"问题生成计划
    if "高铁" in question and ("飞机" in question or "便宜" in question):
        return [
            {"step": 1, "tool": "search", "args": "北京到上海高铁票价", "desc": "查高铁票价"},
            {"step": 2, "tool": "search", "args": "北京到上海机票价格", "desc": "查机票价格"},
            {"step": 3, "tool": "calculate", "args": "1200 - 553", "desc": "计算差价"},
        ]
    # 其他问题：通用三步计划
    return [
        {"step": 1, "tool": "search", "args": question, "desc": "搜索基本信息"},
        {"step": 2, "tool": "calculate", "args": "需要根据第一步结果确定", "desc": "计算相关数值"},
    ]


def summarize_llm(question: str, results: dict) -> str:
    """
    最后一步：汇总所有执行结果，生成最终答案
    """
    # 硬编码模拟：根据收集到的数据生成总结
    train_price = results.get("1", "")
    flight_price = results.get("2", "")
    diff = results.get("3", "")

    if train_price and flight_price:
        return f"{question}\n答案：高铁二等座553元，飞机经济舱800-1200元，高铁比飞机便宜247-647元。"
    return "信息不完整，无法给出准确答案"


# ---- Plan-and-Execute 核心循环 ----

def plan_and_execute(question: str) -> dict:
    """
    Plan阶段: LLM生成完整步骤列表
    Execute阶段: 逐步执行，每步结果存入results dict
    返回: {"answer": str, "plan": list, "results": dict, "steps": int}
    """
    print(f"\n{'='*50}")
    print(f"📋 Phase 1: Planning")
    print(f"{'='*50}")

    # 1. 生成计划
    plan = plan_llm(question)
    print(f"问题: {question}")
    print(f"生成计划 ({len(plan)} 步):")
    for p in plan:
        print(f"  Step {p['step']}: {p['desc']} → {p['tool']}[{p['args']}]")

    # 2. 逐步执行
    print(f"\n{'='*50}")
    print(f"⚡ Phase 2: Executing")
    print(f"{'='*50}")

    results = {}
    for p in plan:
        tool_name = p["tool"]
        tool_args = p["args"]

        if tool_name in TOOLS:
            observation = TOOLS[tool_name](tool_args)
        else:
            observation = f"工具 {tool_name} 不存在"

        results[str(p["step"])] = observation
        print(f"\n  Step {p['step']}: {p['desc']}")
        print(f"  Action: {tool_name}[{tool_args}]")
        print(f"  Result: {observation}")

        # ⚡ 关键区别：这里可以根据前一步结果动态调整后续步骤
        # 比如：如果搜索返回的价格是范围，计算步骤的args需要调整
        # 当前硬编码版本不做动态调整，但框架预留了这个能力

    # 3. 汇总答案
    print(f"\n{'='*50}")
    print(f"📝 Phase 3: Summarizing")
    print(f"{'='*50}")

    answer = summarize_llm(question, results)
    print(f"✅ {answer}")

    return {
        "answer": answer,
        "plan": plan,
        "results": results,
        "steps": len(plan)
    }


# ---- 测试 ----

if __name__ == "__main__":
    result = plan_and_execute("北京到上海高铁票价是多少？比飞机便宜多少？")
    print("\n" + "=" * 60)
    print("最终结果:")
    print(json.dumps(result, indent=2, ensure_ascii=False))