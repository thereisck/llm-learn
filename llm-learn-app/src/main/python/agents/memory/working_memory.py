"""
工作记忆：任务级状态共享（AgenticScope模式）
核心功能：
1. 任务开始时初始化scope
2. 多Agent串行协作时，通过scope传递中间结果
3. 任务结束时清空scope（避免污染下次任务）

类比：人的"正在干的事"，任务结束就忘了
Java直觉：ThreadLocal，方法执行期间共享，方法结束释放
关键原则：任务结束必须清空！否则上次任务的残留变量会污染下次任务
"""
import json
import time

class WorkingMemory:
    """工作记忆：管理当前任务执行过程中的中间状态"""
    def __init__(self):
        self.scope = {}        # 全局共享状态 Dict，所有Agent读写
        self.task_log = []     # 任务执行日志
        self.task_name = ""    # 当前任务名称
        self.task_start = ""   # 任务开始时间
        
    def start_task(self, task_name, initial_state=None):
        """
        开始新任务：初始化scope
        
        Args:
            task_name: 任务名称（如"代码审查流水线"）
            initial_state: 初始状态（如{"code": "def foo(): ..."}）
        """
        # 先清空旧scope（防止上次任务污染）
        if self.scope:
            print(f"[工作记忆] ⚠️ 上次任务'{self.task_name}'的scope未清空，强制清理")
            self._clear_scope()

        self.task_name = task_name
        self.task_start = time.strftime("%H:%M:%S")
        self.scope = initial_state or {}
        self.task_log = []

        print(f"[工作记忆] 任务'{task_name}'启动，初始状态: {list(self.scope.keys())}")
        
    def set(self, key, value, agent_name=""):
        """
        Agent写入scope
        
        关键设计：同名key会覆盖旧值！
        这和LangChain4j AgenticScope一样：
        ScoredCvTailor outputKey("cv") 会覆盖 CvGenerator 写的 cv
        
        Args:
            key: 状态键（如"codeReview"、"securityReport"）
            value: 状态值（可以是字符串、dict、list等）
            agent_name: 写入的Agent名称（用于日志追踪）
        """
        old_value = self.scope.get(key)
        self.scope[key] = value

        # 记录日志
        log_entry = {
            "time": time.strftime("%H:%M:%S"),
            "agent": agent_name,
            "action": "set",
            "key": key,
            "value_preview": str(value)[:80] if isinstance(value, str) else str(value)[:80],
        }
        if old_value is not None:
            log_entry["overwritten"] = True
            log_entry["old_preview"] = str(old_value)[:80] if isinstance(old_value, str) else str(old_value)[:80]
        self.task_log.append(log_entry)

        # 覆盖警告
        if old_value is not None:
            print(f"[工作记忆] ⚠️ Agent'{agent_name}'覆盖了key'{key}'")
            print(f"  旧值: {log_entry['old_preview'][:50]}...")
            print(f"  新值: {log_entry['value_preview'][:50]}...")
    
    def get(self, key, default=None):
        """
        Agent读取scope
        
        Args:
            key: 状态键
            default: 如果key不存在返回的默认值
        """
        value = self.scope.get(key, default)
        if value is default and default is None:
            print(f"[工作记忆] ⚠️ key'{key}'不存在，返回None")
        return value
    
    def get_all_for_agent(self, agent_name, input_keys):
        """
        为Agent准备输入：只读取它需要的keys
        
        这是LangChain4j AgenticScope的核心设计：
        每个Agent通过prompt声明需要读取哪些变量
        
        Args:
            agent_name: Agent名称
            input_keys: 该Agent需要读取的key列表
        
        Returns:
            dict: 只包含该Agent需要的key-value
        """
        result = {}
        missing = []
        for key in input_keys:
            if key in self.scope:
                result[key] = self.scope[key]
            else:
                missing.append(key)

        if missing:
            print(f"[工作记忆] ⚠️ Agent'{agent_name}'需要的keys缺失: {missing}")

        return result
    
    def end_task(self):
        """
        任务结束：保存关键结果到摘要，然后清空scope
        
        关键：不清空就会污染下次任务！
        """
        # 生成任务摘要（用于后续回顾，不存入scope）
        summary = self._generate_task_summary()

        # 清空scope
        self._clear_scope()

        print(f"[工作记忆] 任务'{self.task_name}'完成，scope已清空")
        return summary
    
    def _clear_scope(self):
        """清空scope"""
        self.scope = {}
        self.task_name = ""
        self.task_start = ""

    def _generate_task_summary(self):
        """生成任务执行摘要"""
        summary = f"任务: {self.task_name}\n"
        summary += f"开始: {self.task_start}, 结束: {time.strftime('%H:%M:%S')}\n"
        summary += f"操作次数: {len(self.task_log)}\n"

        # 记录每个Agent的操作
        agent_ops = {}
        for log in self.task_log:
            agent = log["agent"] or "unknown"
            if agent not in agent_ops:
                agent_ops[agent] = 0
            agent_ops[agent] += 1

        summary += f"Agent操作统计: {json.dumps(agent_ops)}\n"

        # 记录最终scope状态
        summary += f"最终状态keys: {list(self.scope.keys())}\n"
        for key, value in self.scope.items():
            preview = str(value)[:100] if isinstance(value, str) else str(value)[:100]
            summary += f"  {key}: {preview}\n"

        return summary
    
    def debug_print(self):
        """调试：打印当前scope状态"""
        print(f"\n=== 工作记忆状态 ===")
        print(f"任务: {self.task_name}")
        print(f"Scope keys: {list(self.scope.keys())}")
        for key, value in self.scope.items():
            preview = str(value)[:80] if isinstance(value, str) else str(value)[:80]
            print(f"  {key}: {preview}")
        print(f"操作日志: {len(self.task_log)}条")
        for log in self.task_log[-5:]:
            agent = log["agent"] or "?"
            action = log["action"]
            key = log["key"]
            overwritten = " (覆盖!)" if log.get("overwritten") else ""
            print(f"  [{log['time']}] {agent} → {action}({key}){overwritten}")
        print("===================\n")
        
# ===== 测试代码：模拟多Agent串行协作 =====
if __name__ == "__main__":
    print("=== 工作记忆测试：多Agent串行协作 ===\n")

    wm = WorkingMemory()

    # ---- 模拟文章加工流水线（参考Week5/6的Editor→Reviewer→Formatter） ----

    # 1. 开始任务
    wm.start_task("文章加工流水线", initial_state={
        "article": "大模型应用开发是2026年最火的技术方向。"
                   "RAG让企业能用自有知识库增强LLM回答质量，"
                   "Agent让LLM从聊天机器人进化为能主动执行任务的智能体。"
                   "但很多团队直接上Agent，不做RAG先夯实知识基础，这是本末倒置。",
    })
    wm.debug_print()

    # 2. Agent A: Editor编辑文章
    print("--- Agent A: Editor ---")
    edited = wm.get("article")  # 读取原始文章
    edited_result = edited.replace("本末倒置", "踩坑后的经验：先做RAG再做Agent")
    edited_result += " \n\n建议学习路线：RAG基础 → Agent范式 → 多Agent协作 → Harness架构。"
    wm.set("editedArticle", edited_result, agent_name="Editor")
    wm.debug_print()

    # 3. Agent B: Reviewer审稿（读取editedArticle）
    print("--- Agent B: Reviewer ---")
    inputs = wm.get_all_for_agent("Reviewer", input_keys=["editedArticle"])
    if "editedArticle" in inputs:
        article_to_review = inputs["editedArticle"]
        # Reviewer输出结构化审稿意见（JSON比自由文本更精简可解析）
        review_result = {
            "score": 7.5,
            "issues": [
                "开头缺少钩子标题",
                "学习路线部分可以更具体",
            ],
            "suggestions": [
                "开头加一句反直觉结论吸引读者",
                "学习路线加时间预估（每阶段2周）",
            ],
        }
        wm.set("reviewResult", review_result, agent_name="Reviewer")
    wm.debug_print()

    # 4. Agent C: Formatter排版（读取editedArticle + reviewResult）
    print("--- Agent C: Formatter ---")
    inputs = wm.get_all_for_agent("Formatter", input_keys=["editedArticle", "reviewResult"])
    if "editedArticle" in inputs and "reviewResult" in inputs:
        # Formatter根据审稿意见修改文章
        final_article = inputs["editedArticle"]
        # 模拟修改：加钩子标题
        final_article = "🔥 为什么90%的团队Agent做不好？因为没先搞RAG\n\n" + final_article
        wm.set("finalArticle", final_article, agent_name="Formatter")
    wm.debug_print()

    # 5. 测试同名key覆盖（核心陷阱！）
    print("\n--- 测试：同名key覆盖 ---")
    wm.set("editedArticle", "这是Reviewer擅自修改后的版本", agent_name="Reviewer")
    wm.debug_print()
    # ⚠️ Editor写的editedArticle被Reviewer覆盖了！
    # 这就是Week5踩过的坑：CodeReviewer 1800字输出塞进{{codeReview}}导致token爆炸
    # 解决方案：每个Agent用不同的outputKey！

    # 6. 任务结束 → 清空scope
    print("\n--- 任务结束 ---")
    summary = wm.end_task()
    print(f"任务摘要:\n{summary}")

    # 7. 验证scope已清空（防止污染下次任务）
    print("\n--- 验证scope已清空 ---")
    wm.debug_print()
    # scope应该是空的！

    # 8. 开始新任务，验证没有旧任务污染
    print("\n--- 开始新任务（验证无污染） ---")
    wm.start_task("代码审查流水线", initial_state={"code": "def login(user, pwd): ..."})
    wm.debug_print()
    # 只有新任务的初始状态，旧任务的reviewResult、finalArticle都不在了

    print("\n=== 核心洞察 ===")
    print("1. scope是Agent间的'胶水'——Editor写editedArticle，Reviewer读它写reviewResult")
    print("2. 同名key会覆盖！这是AgenticScope的核心陷阱——每个Agent必须用不同outputKey")
    print("3. 任务结束必须清空scope——否则旧变量污染新任务")
    print("4. get_all_for_agent让每个Agent只读需要的key——上下文隔离，减少干扰")
    print("5. Java直觉：scope像ThreadLocal/RequestScope，任务结束必须remove")
        
    