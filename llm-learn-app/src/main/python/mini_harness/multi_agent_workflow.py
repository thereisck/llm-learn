"""
多Agent Workflow实战 — 三Agent串行协作流水线
EditorAgent(主编写稿) → ReviewerAgent(审稿提意见) → FormatterAgent(排版润色)

设计原则：
- 对话循环串行化：前一个输出=后一个输入
- 子Agent上下文隔离：每个Agent独立system prompt，不共享历史
- 上下文膨胀控制：Agent间传递精简数据，不传全文
- 参考Hermes Fork模式：invoke≈spawn，返回结果≈merge

Java直觉：
- Workflow ≈ CompletableFuture.thenApply链
- Agent隔离 ≈ 微服务独立ApplicationContext
- 上下文传递 ≈ DTO而非Entity
"""

import os
import json
from datetime import datetime
from llm_client import LLMClient


class BaseAgent:
    """Agent基类 — 独立prompt + 输入输出约定 + LLM调用"""

    def __init__(
        self,
        name: str,
        system_prompt: str,
        llm: LLMClient,
        output_format: str = "text",
        output_keys: list[str] = None,
        max_tokens: int = 800,
    ):
        self.name = name
        self.system_prompt = system_prompt
        self.llm = llm
        self.output_format = output_format
        self.output_keys = output_keys or []
        self.max_tokens = max_tokens

    def invoke(self, input_data: str) -> str:
        """调用Agent — 组装messages→LLM推理→解析输出"""
        messages = [
            {"role": "system", "content": self.system_prompt},
            {"role": "user", "content": input_data},
        ]

        if self.output_format == "json" and self.output_keys:
            messages[0]["content"] += f"\n\n{self._format_instruction()}"

        response_text = self.llm.chat(messages, max_tokens=self.max_tokens)

        if self.output_format == "json":
            return self._extract_json(response_text)

        return response_text

    def _format_instruction(self) -> str:
        """JSON输出格式约束"""
        keys_desc = ", ".join(self.output_keys)
        example = ", ".join([f'"{k}": "..."' for k in self.output_keys])
        return (
            f"你必须以JSON格式输出，包含字段: {keys_desc}\n"
            f"不要加三个反引号json标记，不要输出非JSON内容\n"
            f"示例: {{{example}}}"
        )

    def _extract_json(self, text: str) -> str:
        """从LLM输出提取JSON — 去markdown标记 + 解析验证"""
        cleaned = text.strip()
        # 去三个反引号包裹
        if cleaned.startswith("```"):
            idx = cleaned.find("\n")
            if idx > 0:
                cleaned = cleaned[idx + 1:]
            if cleaned.endswith("```"):
                cleaned = cleaned[:-3].strip()

        try:
            parsed = json.loads(cleaned)
            return json.dumps(parsed, ensure_ascii=False)
        except json.JSONDecodeError:
            print(f"[{self.name}] JSON解析失败，返回原始文本")
            return text

    def __repr__(self):
        return f"BaseAgent(name={self.name}, format={self.output_format})"


# ===== 三种Agent定义 =====

EDITOR_PROMPT = """你是一位技术文章主编，擅长写深度技术分析文章。

你的任务：根据给定主题，写一篇技术文章初稿。

要求：
- 标题要有钩子（反直觉/踩坑/数据对比），不用正式标题
- 开头200字放核心结论
- 至少1500字
- 有代码示例和实际案例
- 口语化风格，不要学术腔

当前时间: {time}"""

REVIEWER_PROMPT = """你是一位严厉的技术审稿人，只关心文章质量。

你的任务：审查文章初稿，输出结构化的修改意见。

审查维度：
1. 标题吸引力 — 是否有钩子？
2. 开头结论 — 前200字是否直击要点？
3. 技术深度 — 有没有代码/数据/实验支撑？
4. 口语化程度 — 有没有AI味（首先/其次/综上所述）？
5. 逻辑连贯 — 段落之间是否自然过渡？

你必须以JSON格式输出，包含字段: score, issues, suggestions
- score: 1-10评分
- issues: 发现的问题列表
- suggestions: 具体修改建议列表"""

FORMATTER_PROMPT = """你是一位技术文章排版润色师，只做最后一遍打磨。

你的任务：根据审稿意见，对文章做最终润色排版。

润色要点：
- 根据suggestions逐条修改
- 去AI味：首先/其次/综上所述 → 先说最重要的/另外/说白了
- 标题如果不够钩子，改一个
- 确保开头200字是核心结论
- 加粗关键词，代码块用三个反引号包裹"""

EDITOR_KEYS = []  # text格式不需要output_keys
REVIEWER_KEYS = ["score", "issues", "suggestions"]
FORMATTER_KEYS = []


def create_editor(llm: LLMClient) -> BaseAgent:
    """创建主编Agent"""
    prompt = EDITOR_PROMPT.format(time=datetime.now().strftime("%Y-%m-%d %A %H:%M"))
    return BaseAgent(name="editor", system_prompt=prompt, llm=llm, output_format="text", max_tokens=2000)


def create_reviewer(llm: LLMClient) -> BaseAgent:
    """创建审稿Agent — JSON输出，便于下游消费"""
    return BaseAgent(
        name="reviewer",
        system_prompt=REVIEWER_PROMPT,
        llm=llm,
        output_format="json",
        output_keys=REVIEWER_KEYS,
    )


def create_formatter(llm: LLMClient) -> BaseAgent:
    """创建排版Agent"""
    return BaseAgent(name="formatter", system_prompt=FORMATTER_PROMPT, llm=llm, output_format="text", max_tokens=2000)


# ===== Workflow编排器 =====

class ArticleWorkflow:
    """
    三Agent串行Workflow — 编辑→审稿→排版

    设计关键：
    1. 串行执行 — editor→reviewer→formatter，顺序不可变
    2. 上下文隔离 — 每个Agent只看到自己的输入，不继承上一个的对话历史
    3. 传递精简 — reviewer输出JSON（score+issues+suggestions），不是1800字全文
    4. 结果merge — formatter拿到原文+审稿意见，做最终润色
    """

    def __init__(self, llm: LLMClient):
        self.editor = create_editor(llm)
        self.reviewer = create_reviewer(llm)
        self.formatter = create_formatter(llm)

    def run(self, topic: str) -> dict:
        """
        执行完整Workflow — 三步串行

        返回dict包含每个Agent的中间输出，便于调试
        """
        print(f"\n{'='*60}")
        print(f"Article Workflow 启动 — 主题: {topic}")
        print(f"{'='*60}")

        # Step 1: 主编写稿
        print(f"\n[Step 1] {self.editor.name} 开始写稿...")
        draft = self.editor.invoke(topic)
        print(f"[Step 1] 完成 — 初稿 {len(draft)} 字")

        # Step 2: 审稿
        # 关键：只传初稿给reviewer，不传editor的system prompt或历史
        print(f"\n[Step 2] {self.reviewer.name} 开始审稿...")
        review_json = self.reviewer.invoke(draft)
        print(f"[Step 2] 完成 — 审稿意见: {review_json[:100]}...")

        # Step 3: 排版润色
        # 关键：formatter同时拿到原文+审稿意见，两个输入合并
        print(f"\n[Step 3] {self.formatter.name} 开始润色...")
        formatter_input = self._merge_for_formatter(draft, review_json)
        final_article = self.formatter.invoke(formatter_input)
        print(f"[Step 3] 完成 — 终稿 {len(final_article)} 字")

        # 返回完整结果
        result = {
            "topic": topic,
            "draft": draft,
            "review": review_json,
            "final": final_article,
            "stats": {
                "draft_chars": len(draft),
                "final_chars": len(final_article),
                "timestamp": datetime.now().isoformat(),
            },
        }

        self._print_summary(result)
        return result

    def _merge_for_formatter(self, draft: str, review_json: str) -> str:
        """
        合并原文+审稿意见 → formatter输入

        上下文膨胀控制的关键：
        只传两样东西：原文 + 结构化审稿意见
        对比Week5踩坑：CodeReviewer 1800字输出塞进{{codeReview}}导致token爆炸
        """
        try:
            review_data = json.loads(review_json)
            score = review_data.get("score", "N/A")
            issues = review_data.get("issues", [])
            suggestions = review_data.get("suggestions", [])
            review_summary = f"评分: {score}/10\n问题: {issues}\n建议: {suggestions}"
        except json.JSONDecodeError:
            review_summary = review_json  # fallback

        return f"## 文章初稿\n{draft}\n\n## 审稿意见\n{review_summary}\n\n请根据审稿意见润色这篇文章。"

    def _print_summary(self, result: dict):
        """打印Workflow结果摘要"""
        stats = result["stats"]
        print(f"\n{'='*60}")
        print(f"Workflow 完成!")
        print(f"  初稿: {stats['draft_chars']} 字")
        print(f"  终稿: {stats['final_chars']} 字")
        print(f"  增减: {stats['final_chars'] - stats['draft_chars']} 字")
        print(f"{'='*60}")


# ===== 入口 =====

if __name__ == "__main__":
    api_key = os.environ.get("LLM_API_KEY", "sk-kemgxaacaqdehipgoywavavmpaiazkocfjyixcvsnslmeycv")
    base_url = os.environ.get("LLM_BASE_URL", "https://api.siliconflow.cn/v1")
    model = os.environ.get("LLM_MODEL", "Pro/zai-org/GLM-5.1")

    llm = LLMClient(api_key=api_key, base_url=base_url, model=model)
    workflow = ArticleWorkflow(llm)

    # 测试主题
    topic = input("请输入文章主题: ").strip()
    if not topic:
        topic = "为什么RAG系统不用微调：三个实战教训"

    result = workflow.run(topic)

    # 保存终稿到文件
    output_dir = os.path.dirname(os.path.abspath(__file__))
    output_file = os.path.join(output_dir, f"article_{topic[:20].replace(' ', '_')}.md")
    with open(output_file, "w", encoding="utf-8") as f:
        f.write(result["final"])
    print(f"\n终稿已保存: {output_file}")