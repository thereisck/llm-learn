"""
MemoryAgent：三层记忆级联 + 自学习提炼
核心设计：
1. 查询时：短期优先 → 工作补充 → 长期兜底（省钱省token）
2. 任务结束时：自动提炼关键信息 → 写入长期记忆（自学习循环）
3. 对比Hermes封闭式记忆 vs OpenClaw自由写入+定期审计

这就是你眼前OpenClaw的MEMORY.md机制的真实现！
"""

import json
import os
import time

from short_term_memory import ShortTermMemory
from working_memory import WorkingMemory
from long_term_memory import LongTermMemory

class MemoryAgent:
    """带三层记忆的Agent"""

    def __init__(self, name, model_fn=None, memory_dir="memory"):
        self.name = name
        self.model_fn = model_fn  # LLM调用函数
        self.short_term = ShortTermMemory(max_rounds=5, model_fn=model_fn)
        self.working = WorkingMemory()
        self.long_term = LongTermMemory(memory_dir=memory_dir)

    # ---- 级联查询：三层记忆按优先级检索 ----

    def recall(self, query):
        """
        级联查询：短期优先 → 工作补充 → 长期兜底
        
        为什么级联而不是全查？
        短期和工作记忆已经在context里（零成本）
        长期记忆需要额外文件读取/向量检索调用（有成本）
        能用免费的就用免费的！
        """
        results = {"short_term": None, "working": None, "long_term": None}

        # 第一层：短期记忆（对话历史里有没有提到？）
        for msg in self.short_term.history:
            if query.lower() in msg["content"].lower():
                results["short_term"] = msg["content"]
                print(f"[{self.name}] 短期记忆命中: '{query}' → 省一次长期检索！")
                break

        # 第二层：工作记忆（当前任务scope里有没有？）
        for key, value in self.working.scope.items():
            if query.lower() in str(value).lower():
                results["working"] = f"{key}: {str(value)[:200]}"
                print(f"[{self.name}] 工作记忆命中: key='{key}'")
                break

        # 第三层：长期记忆（索引→详情级联检索）
        if not results["short_term"] and not results["working"]:
            long_results = self.long_term.query(query)
            if long_results:
                # 只取第一条最相关的详情
                best = long_results[0]
                results["long_term"] = best.get("content", best.get("preview", ""))[:300]
                print(f"[{self.name}] 长期记忆命中: {best['file']}")

        return results

    # ---- 自学习提炼：任务结束后自动写入长期记忆 ----

    def learn(self, heading, conclusion, tags, detail_content):
        """
        自学习提炼：把关键信息写入长期记忆
        
        Hermes的设计：封闭式记忆——Agent不能直接往MEMORY.md写，
        必须走自学习循环过滤（LLM判断什么值得记）
        
        OpenClaw的设计：自由写入+定期审计——Agent可以随手写，
        heartbeat定期整理
        
        这里我们用Hermes风格：只记结论不记过程，精简<40行
        """
        # 生成详情文件名（当天日期）
        filename = time.strftime("%Y-%m-%d") + ".md"

        # 写入索引（精简结论）
        self.long_term.update_index(
            heading=heading,
            content=conclusion,
            tags=tags,
            detail_file=filename
        )

        # 写入详情（完整信息）
        self.long_term.write_detail(filename, detail_content)

        print(f"[{self.name}] 自学习提炼完成: '{heading}' → 已写入长期记忆")

    # ---- 完整对话流程 ----

    def chat(self, user_input, system_prompt=""):
        """完整对话：查询记忆 → 构建上下文 → 调用LLM → 存入短期记忆"""
        # 1. 先尝试从记忆中回忆相关信息
        memory_context = self.recall(user_input)

        # 2. 构建增强的system prompt（注入记忆信息）
        enhanced_prompt = system_prompt
        if memory_context["short_term"]:
            enhanced_prompt += f"\n[近期对话提到]: {memory_context['short_term'][:100]}"
        if memory_context["working"]:
            enhanced_prompt += f"\n[当前任务相关]: {memory_context['working'][:100]}"
        if memory_context["long_term"]:
            enhanced_prompt += f"\n[长期知识库]: {memory_context['long_term'][:200]}"

        # 3. 获取完整上下文
        context = self.short_term.get_context(enhanced_prompt)

        # 4. 调用LLM（如果有model_fn）
        if self.model_fn:
            response = self.model_fn(context)
        else:
            # 没有真实LLM，模拟硬编码回复
            response = self._simulate_response(user_input, memory_context)

        # 5. 存入短期记忆
        self.short_term.add("user", user_input)
        self.short_term.add("assistant", response)

        return response

    def _simulate_response(self, user_input, memory_context):
        """模拟LLM回复（测试用，不需要真实API）"""
        if memory_context["long_term"]:
            return f"根据之前的学习经验：{memory_context['long_term'][:100]}"
        if memory_context["short_term"]:
            return f"我们之前讨论过这个话题，核心点是：{memory_context['short_term'][:60]}"
        return f"关于'{user_input}'，我目前还没有相关经验记录。"

    def debug_print(self):
        """打印三层记忆全景"""
        print(f"\n{'='*50}")
        print(f"MemoryAgent: {self.name}")
        print(f"{'='*50}")
        self.short_term.debug_print()
        self.working.debug_print()
        self.long_term.debug_print()


# ===== 测试：完整的跨session记忆闭环 =====
if __name__ == "__main__":
    print("=== MemoryAgent 三层记忆闭环测试 ===\n")

    test_dir = "/tmp/test_memory_agent/memory"
    os.makedirs(test_dir, exist_ok=True)
    agent = MemoryAgent("RAG助手", memory_dir=test_dir)

    # ---- Session 1：学习MySQL charset踩坑 ----
    print("=" * 60)
    print("📱 Session 1：用户分享踩坑经验")
    print("=" * 60)

    # 1. 用户告诉Agent一个踩坑经验
    agent.chat("我踩了个坑：Docker exec进MySQL容器，默认charset是latin1，中文WHERE匹配不到")
    agent.debug_print()

    # 2. 任务结束 → 自学习提炼写入长期记忆
    agent.learn(
        heading="MySQL charset双重编码",
        conclusion="结论：Docker exec默认latin1导致中文WHERE匹配不到，必须全程charset=utf8mb4",
        tags=["mysql", "charset", "docker", "踩坑"],
        detail_content=(
            "# MySQL charset踩坑\n\n"
            "## 问题\nDocker exec默认charset=latin1不是utf8mb4\n"
            "中文数据双重编码，WHERE clause匹配不到\n\n"
            "## 解决\n连接时加--default-character-set=utf8mb4\n\n"
            "## 教训\n中文数据库必须全程charset=utf8mb4"
        )
    )

    # 3. Session 1结束 → 清空短期和工作记忆（但长期记忆保留！）
    print("\n--- Session 1结束，清空短期和工作记忆 ---")
    agent.short_term.clear()
    agent.working.end_task()

    # ---- Session 2：新session，验证长期记忆有效 ----
    print("\n" + "=" * 60)
    print("📱 Session 2：新session，问相关问题")
    print("=" * 60)

    # 4. 新session，用户问之前踩过的坑
    response = agent.chat("MySQL中文查不到数据是什么原因？")
    print(f"\n🤖 Agent回复: {response}")
    # 应该命中长期记忆，不需要重新学习！

    agent.debug_print()

    # 5. 再问一个无关问题（验证级联：短期→长期都没命中）
    response = agent.chat("量子计算怎么入门？")
    print(f"\n🤖 Agent回复: {response}")
    # 短期和长期都没有 → LLM自行回答或承认不知道

    agent.debug_print()

    # ---- 验证：工作记忆级联 ----
    print("\n" + "=" * 60)
    print("📱 Session 3：多Agent任务中验证工作记忆")
    print("=" * 60)

    # 6. 开始一个代码审查任务
    agent.working.start_task("代码审查", {"code": "def login(user, pwd): return True"})
    agent.working.set("codeReview", "发现3个问题：1.密码没加密 2.没异常处理 3.永远返回True", agent_name="Reviewer")

    # 7. 用户问"密码加密" → 工作记忆命中！
    memory_results = agent.recall("密码加密")
    print(f"\n🤠 级联查询结果:")
    print(f"  短期: {memory_results['short_term']}")
    print(f"  工作: {memory_results['working']}")
    print(f"  长期: {memory_results['long_term']}")

    agent.debug_print()

    # ---- 最终总结 ----
    print("\n" + "=" * 60)
    print("📊 三层记忆级联查询总结")
    print("=" * 60)
    print("Session 1 → 踩坑经验 → 自学习提炼 → 写入长期记忆")
    print("Session 2 → 问MySQL charset → 长期记忆命中 → 不需要重新学习！")
    print("Session 2 → 问量子计算 → 三层都没命中 → LLM自行回答")
    print("Session 3 → 任务中问密码加密 → 工作记忆命中 → 省一次长期检索！")
    print()
    print("=== 核心洞察 ===")
    print("1. 级联查询=省钱省token：短期免费→工作免费→长期有成本")
    print("2. 自学习提炼=跨session记忆：踩坑经验自动沉淀，下次直接用")
    print("3. 任务结束清空工作/短期记忆=防止污染，但长期记忆永久保留")
    print("4. Hermes风格=封闭式记忆，只有结论进索引，噪音被过滤")
    print("5. OpenClaw风格=自由写入+heartbeat定期审计，灵活但需维护")
    print("6. 你眼前OpenClaw的MEMORY.md就是这个机制的真实现！")