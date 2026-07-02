"""
循环Workflow：反思迭代 — 循环直到质量达标
核心概念：Editor写稿 → Reviewer评分 → 不达标就重写循环

对比Week5的Reflection：
- Reflection：做完自评不满意重来 → LLM自己决定"不满意"
- Looping：写稿→评分→循环 → 阈值(代码)决定"是否达标"

关键区别：退出条件是代码判断的阈值(score < 7)，不是LLM说"好了就行"

Java直觉：Looping = while(score < threshold) { improve(); }
         Reflection = selfReflect() → LLM自主判断要不要重来
"""

import os
import json
import sys
import re

sys.path.append(os.path.join(os.path.dirname(__file__), "..", "..", "mini_harness"))
from llm_client import LLMClient

class EditorAgent:
    """
    编辑Agent — 写文章/改文章
    
    第一次：根据主题写初稿
    循环中：根据Reviewer的修改意见重写
    
    设计原则：
    - 初稿和修改稿用同一个Agent（同一个system prompt）
    - 修改时把Reviewer的意见塞进输入 → Agent知道哪里不好
    - 每次输出控制在600字以内 → 防止上下文膨胀
    """

    def __init__(self, llm: LLMClient):
        self.llm = llm
        self.system_prompt = (
            "你是一个技术文章编辑。\n"
            "输出要求：\n"
            "1. 直接输出文章内容，不要加标题、前言、废话\n"
            "2. 控制在600字以内\n"
            "3. 如果有修改意见，针对性改进，不要全盘重写\n"
            "4. 语言简洁有力，避免空洞的形容词"
        )

    def invoke(self, task: str, feedback: str = None, previous_draft: str = None) -> str:
        if feedback and previous_draft:
            user_msg = (
                f"上一版文章：\n{previous_draft}\n\n"
                f"审稿意见（请针对这些问题改进）：\n{feedback}\n\n"
                f"请改进文章，直接输出改进后的内容。"
            )
        else:
            user_msg = f"请写一篇关于「{task}」的技术短文，600字以内。"

        messages = [
            {"role": "system", "content": self.system_prompt},
            {"role": "user", "content": user_msg},
        ]
        result = self.llm.chat(messages, max_tokens=2000)
        print(f"[Editor] 输出完成，长度={len(result)}字")
        return result
    
class ReviewerAgent:
    """
    审稿Agent — 评分 + 给修改意见
    
    输出格式：JSON字符串
    {"score": 7, "issues": ["问题1"], "suggestions": ["建议1"]}
    
    设计原则：
    - 输出JSON而非自由文本 → 可解析、可量化、可比较
    - score是退出条件的关键 → 代码判断score >= threshold就停止
    - issues是Editor重写的方向 → 不达标时告诉Editor哪里不好
    """

    def __init__(self, llm: LLMClient):
        self.llm = llm
        self.system_prompt = (
            "你是一个严格的文章审稿人。\n"
            "评分标准：1-10分，7分以上为达标\n"
            "输出格式：必须严格输出JSON，不要加任何其他文字\n"
            "JSON格式：{\"score\": 数字, \"issues\": [\"问题列表\"], \"suggestions\": [\"建议列表\"]}\n"
            "评分维度：\n"
            "1. 逻辑清晰（2分）\n"
            "2. 内容充实（2分）\n"
            "3. 语言简洁（2分）\n"
            "4. 有实际价值（2分）\n"
            "5. 无AI味（2分）\n"
            "低于7分时，issues和suggestions必须具体"
        )

    def invoke(self, draft: str, topic: str) -> dict:
        messages = [
            {"role": "system", "content": self.system_prompt},
            {"role": "user", "content": f"主题：{topic}\n\n文章内容：\n{draft}\n\n请审稿评分。"},
        ]
        result = self.llm.chat(messages, max_tokens=1000)
        print(f"[Reviewer] 原始输出：{result[:200]}")
        return self._parse_review(result)

    def _parse_review(self, raw: str) -> dict:
        """从LLM输出中提取JSON评分 — 容错解析"""
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            pass

        match = re.search(r'\{[^{}]*\}', raw)
        if match:
            try:
                return json.loads(match.group())
            except json.JSONDecodeError:
                pass

        score_match = re.search(r'"score"\s*:\s*(\d+)', raw)
        if score_match:
            return {
                "score": int(score_match.group(1)),
                "issues": ["JSON解析失败"],
                "suggestions": [],
            }

        print("[Reviewer] JSON解析全部失败，返回默认评分")
        return {"score": 1, "issues": ["审稿输出格式异常"], "suggestions": ["请重新审稿"]}
    
class LoopingWorkflow:
    """
    循环Workflow — Editor写稿 → Reviewer评分 → 不达标就循环改进
    
    核心机制：
    - while score < threshold: 改进 → 再评分
    - max_iterations: 最多循环N次（防止死循环）
    - threshold: 代码判断的达标阈值
    
    对比Reflection：LLM自评→LLM说了算 / Looping→代码阈值说了算
    
    Java直觉：
    int score;
    int maxRetry = 3;
    do {
        draft = editor.write(feedback);
        score = reviewer.score(draft);
        feedback = reviewer.getIssues();
    } while (score < 7 && retry++ < maxRetry);
    """

    def __init__(
        self,
        editor: EditorAgent,
        reviewer: ReviewerAgent,
        threshold: int = 7,
        max_iterations: int = 3,
    ):
        self.editor = editor
        self.reviewer = reviewer
        self.threshold = threshold
        self.max_iterations = max_iterations

    def run(self, topic: str) -> dict:
        """
        执行循环迭代流程
        
        流程：
        1. Editor写初稿
        2. Reviewer评分
        3. score < threshold → 把意见反馈给Editor重写
        4. 重复2-3，直到达标或超过max_iterations
        """
        history = []
        draft = None
        review = None

        for i in range(self.max_iterations):
            iteration = i + 1
            print(f"\n{'='*60}")
            print(f"🔄 第 {iteration} 轮迭代")
            print(f"{'='*60}")

            # Step 1: Editor写稿/改稿
            if i == 0:
                draft = self.editor.invoke(task=topic)
            else:
                feedback_text = self._format_feedback(review)
                draft = self.editor.invoke(
                    task=topic,
                    feedback=feedback_text,
                    previous_draft=draft,
                )

            # Step 2: Reviewer评分
            review = self.reviewer.invoke(draft=draft, topic=topic)
            score = review.get("score", 0)

            history.append({
                "iteration": iteration,
                "draft_length": len(draft),
                "score": score,
                "issues": review.get("issues", []),
                "suggestions": review.get("suggestions", []),
                "draft_preview": draft[:200],
            })

            print(f"评分: {score}/10")
            print(f"问题: {review.get('issues', [])}")

            # Step 3: 代码判断是否达标
            if score >= self.threshold:
                print(f"\n✅ 达标！score={score} >= threshold={self.threshold}")
                print(f"总共迭代 {iteration} 次")
                break
            else:
                print(f"\n❌ 未达标 score={score} < threshold={self.threshold}")
                if iteration < self.max_iterations:
                    print(f"进入第 {iteration+1} 轮改进...")
                else:
                    print(f"已达最大循环次数 {self.max_iterations}，强制结束")

        final_score = review.get("score", 0)
        success = final_score >= self.threshold

        return {
            "topic": topic,
            "success": success,
            "final_score": final_score,
            "threshold": self.threshold,
            "total_iterations": len(history),
            "history": history,
            "final_draft": draft,
        }

    def _format_feedback(self, review: dict) -> str:
        """把Reviewer的JSON评分格式化成Editor能理解的文字"""
        issues = review.get("issues", [])
        suggestions = review.get("suggestions", [])
        score = review.get("score", 0)

        feedback = f"当前评分：{score}/10（需达到{self.threshold}分才能通过）\n\n"
        if issues:
            feedback += "存在的问题：\n"
            for idx, issue in enumerate(issues, 1):
                feedback += f"  {idx}. {issue}\n"
        if suggestions:
            feedback += "\n改进建议：\n"
            for idx, s in enumerate(suggestions, 1):
                feedback += f"  {idx}. {s}\n"

        return feedback
    
    
def main():
    """测试循环Workflow"""

    llm = LLMClient()
    editor = EditorAgent(llm)
    reviewer = ReviewerAgent(llm)

    workflow = LoopingWorkflow(
        editor=editor,
        reviewer=reviewer,
        threshold=7,
        max_iterations=3,
    )

    result = workflow.run(topic="为什么Agent需要确定性路由而不是让LLM自己决策")

    # 打印最终结果
    print(f"\n{'='*60}")
    print("📊 循环Workflow最终结果")
    print(f"{'='*60}")
    print(f"达标: {result['success']}")
    print(f"最终评分: {result['final_score']}/10")
    print(f"总迭代次数: {result['total_iterations']}")
    print("\n迭代历史：")
    for h in result['history']:
        print(f"  第{h['iteration']}轮: score={h['score']}, 稿件长度={h['draft_length']}字")
        if h['issues']:
            print(f"    问题: {h['issues']}")

    print("\n最终稿件（前500字）：")
    print(result['final_draft'][:500])

    # 核心洞察总结
    print(f"\n{'='*60}")
    print("🧠 核心洞察：Looping vs Reflection")
    print(f"{'='*60}")
    print("""
    Reflection（Week5 Day1）：
      - LLM自评"满意吗？" → LLM说了算
      - 可能自评满意但实际不好
      - 适合简单任务，成本低

    Looping（今天学的）：
      - Reviewer评分 → 代码判断score>=7 → 代码说了算
      - 退出条件是确定性阈值
      - 每轮有明确改进方向（Reviewer的issues+suggestions）
      - 适合质量敏感任务，成本高但结果可控

    实践选择：
      - 快速验证 → Reflection（省token）
      - 质量保证 → Looping（有明确达标标准）
      - 最好组合：先Reflection自评 + Looping兜底阈值
    """)


if __name__ == "__main__":
    main()