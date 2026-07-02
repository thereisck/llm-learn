"""
短期记忆：对话历史管理
核心功能：
1. 添加消息到历史
2. 滑动窗口截断（只保留最近N轮）
3. 摘要压缩（旧对话压缩成一段摘要）

类比：人的"刚才说了啥"，对话结束就没了
Java直觉：HttpSession，存最近几次请求状态
"""
import json
import os
import time

class ShortTermMemory:
    """短期记忆：管理当前对话的消息历史"""
    def __init__(self, max_rounds=5, model_fn=None):
        """
        Args:
            max_rounds: 滑动窗口保留的最大对话轮数（1轮=user+assistant）
            model_fn: LLM调用函数，用于摘要压缩。None时用简单截断
        """
        self.max_rounds = max_rounds
        self.model_fn = model_fn
        self.history = []  # [{"role":"user/assistant/system/tool","content":"..."}]
        self.summary = ""  # 旧对话压缩后的摘要
        
    def add(self, role, content):
        """添加一条消息到历史"""
        self.history.append({
            "role": role,
            "content": content,
            "timestamp": time.strftime("%H:%M:%S")
        })
        # 添加后检查是否超出窗口，需要压缩
        self._compress_if_needed()
        
    def get_context(self, system_prompt=""):
        """获取完整的LLM调用上下文"""
        messages = []
        # 1. system prompt（如果有）
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})

        # 2. 摘要（如果有压缩过的旧对话）
        if self.summary:
            messages.append({
                "role": "system",
                "content": f"[之前的对话摘要]\n{self.summary}"
            })

        # 3. 最近的历史（滑动窗口内的）
        messages.extend([
            {"role": m["role"], "content": m["content"]}
            for m in self.history
        ])

        return messages
    
    def count_tokens_approx(self):
        """粗略估算token数（中文约1.5字/token，英文约4字/token）"""
        total_chars = len(self.summary) + sum(
            len(m["content"]) for m in self.history
        )
        # 简化估算：平均2字/token
        return total_chars // 2
    
    def _compress_if_needed(self):
        """检查是否超出窗口，触发压缩"""
        # 计算当前轮数（user+assistant算1轮）
        rounds = sum(1 for m in self.history if m["role"] == "user")

        if rounds <= self.max_rounds:
            return  # 还在窗口内，不需要压缩

        # 超出窗口了，需要把旧对话压缩成摘要
        old_messages = self.history[:-self.max_rounds * 2]  # 每轮2条消息
        recent_messages = self.history[-self.max_rounds * 2:]

        # 压缩旧对话
        if self.model_fn:
            # 有LLM → 用LLM生成摘要（质量好但有成本）
            new_summary = self._llm_summarize(old_messages)
        else:
            # 没有LLM → 用简单拼接截断（零成本但质量差）
            new_summary = self._simple_summarize(old_messages)

        # 更新摘要 + 只保留最近的对话
        self.summary = new_summary
        self.history = recent_messages

        print(f"[短期记忆] 压缩完成：{len(old_messages)}条旧消息 → 摘要{len(new_summary)}字，"
              f"保留最近{len(recent_messages)}条")
        
    def _llm_summarize(self, old_messages):
        """用LLM生成高质量摘要"""
        prompt = "请用2-3句话总结以下对话的关键信息（只记结论，不记过程）：\n\n"
        for m in old_messages:
            prompt += f"{m['role']}: {m['content']}\n"

        result = self.model_fn(prompt)
        return result if result else self._simple_summarize(old_messages)
    
    def _simple_summarize(self, old_messages):
        """简单摘要：拼接每条消息的前50字"""
        parts = []
        for m in old_messages:
            snippet = m["content"][:50]
            if len(m["content"]) > 50:
                snippet += "..."
            parts.append(f"{m['role']}说: {snippet}")

        summary = "；".join(parts)
        # 摘要也有长度限制，太长就截断
        max_summary_len = 500
        if len(summary) > max_summary_len:
            summary = summary[:max_summary_len] + "...(更早的对话已遗忘)"

        return summary
    
    def clear(self):
        """清空所有短期记忆（对话结束）"""
        self.history = []
        self.summary = ""
        print("[短期记忆] 已清空")
        
    def debug_print(self):
        """调试：打印当前状态"""
        rounds = sum(1 for m in self.history if m["role"] == "user")
        print(f"\n=== 短期记忆状态 ===")
        print(f"摘要长度: {len(self.summary)}字")
        print(f"历史轮数: {rounds}/{self.max_rounds}")
        print(f"估算tokens: {self.count_tokens_approx()}")
        if self.summary:
            print(f"摘要内容: {self.summary[:100]}...")
        print(f"最近消息:")
        for m in self.history[-4:]:
            print(f"  [{m['timestamp']}] {m['role']}: {m['content'][:60]}...")
        print("===================\n")
        
# ===== 测试代码 =====
if __name__ == "__main__":
    print("=== 短期记忆测试 ===\n")

    # 不用真实LLM，用简单截断模式测试
    memory = ShortTermMemory(max_rounds=3)

    # 模拟6轮对话（超出3轮窗口）
    questions = [
        "什么是RAG？",
        "RAG和微调有什么区别？",
        "向量数据库怎么选？",
        "Chunk切分多大合适？",
        "Rerank模型推荐哪个？",
        "企业级RAG最小配置是什么？",
    ]
    answers = [
        "RAG是检索增强生成，先从知识库检索相关文档再让LLM生成回答",
        "RAG是动态检索知识，微调是把知识固化到模型参数里，RAG更灵活",
        "Chroma适合开发测试，Milvus适合生产，ES适合已有ES基础设施的团队",
        "512-1024字符是常用范围，中文建议512，英文可以1024",
        "bge-reranker-v2-m3效果好，但需要GPU；Cohere Rerank是API调用无需GPU",
        "最小配置：Hybrid检索+threshold0.5+固定切分512+topK5，Rerank和多路召回是可选优化",
    ]

    for i, (q, a) in enumerate(zip(questions, answers)):
        print(f"\n--- 第{i+1}轮对话 ---")
        memory.add("user", q)
        memory.add("assistant", a)
        memory.debug_print()

    # 最终状态
    print("\n=== 最终上下文（将发给LLM的内容） ===")
    context = memory.get_context(system_prompt="你是RAG技术专家")
    for msg in context:
        role_tag = {"system": "🔧", "user": "👤", "assistant": "🤖"}.get(msg["role"], "❓")
        print(f"{role_tag} [{msg['role']}] {msg['content'][:80]}...")

    print("\n=== 核心洞察 ===")
    print("1. 滑动窗口只保留最近N轮 → token不会无限增长")
    print("2. 旧对话被压缩成摘要 → 关键信息不丢失但细节会丢失")
    print("3. 摘要 + 最近对话 = LLM能看到的完整上下文")
    print("4. 窗口大小是成本vs质量的权衡：小窗口省钱但丢失更多上下文")