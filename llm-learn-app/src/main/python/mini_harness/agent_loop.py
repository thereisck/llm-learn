"""
Agent对话循环 — 串起Session+Permission+Memory+Skill四大模块
参考：OpenClaw intake→context→inference→tool→output→persistence 完整流程

对话循环（9步）：
1. 消息到达 → Session入队
2. 串行取出 → 同一Session同时只处理一条
3. 加载记忆 → MEMORY.md + 最近日志
4. Skill注入 → 根据用户输入自动匹配Skill，注入prompt
5. 权限评估 → deny→ask→allow检查本次操作需要什么权限
6. Agent处理 → （目前是echo+Skill增强，后续接入LLM）
7. 权限交互 → 如果需要批准，交互式询问用户
8. 记忆写入 → 重要结论写入MEMORY.md或日志
9. 输出回复 → 流式输出给用户
"""
import sys
import os
# 确保同目录模块可被import（不管从哪个目录运行）
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import asyncio
from datetime import datetime, date

from session import SessionManager, Message, Response, Session
from permissions import PermissionProfile, ToolLevel, Decision, RememberScope
from memory import MemoryManager, MemoryEntry, DailyLog
from skills import SkillsManager
from llm_client import LLMClient
from tools import ToolRegistry
from tool_executor import ToolExecutor
from compaction import Compactor, CompactionTrigger


class MiniHarness:
    """
    Mini Harness — 迷你版Agent Harness
    
    把5个模块串起来的完整对话循环：
    Session(串行化) → Permission(权限) → Memory(记忆) → Skill(能力) → LLM(推理)
    """
    
    def __init__(
        self,
        memory_dir: str = "memory_dir",
        skill_dirs: dict = None,
        permission_mode: str = "default",
        max_concurrent: int = 5,
        llm_api_key: str = "",
        llm_base_url: str = "",
        llm_model: str = "",
    ):
        # 六大模块初始化
        self.session_mgr = SessionManager(max_concurrent=max_concurrent)
        self.permissions = PermissionProfile(mode=permission_mode)
        self.memory = MemoryManager(memory_dir)
        self.skills = SkillsManager(skill_dirs or {})
        self.skills.load_all()
        self.llm = LLMClient(api_key=llm_api_key, base_url=llm_base_url, model=llm_model)
        self.tool_registry = ToolRegistry()
        self.tool_executor = ToolExecutor(self.tool_registry)
        self.compactor = Compactor(llm_client=self.llm)
        
        # 绑定permission的session
        self.permissions.set_session("default")
    
    async def run_interactive(self):
        """
        交互式运行 — 完整对话循环
        
        这是整个Mini Harness的主入口
        """
        session = self.session_mgr.create_session("default")
        
        print("=" * 60)
        print("Mini Harness v2.1 — 完整对话循环 + LLM + Tool执行 + Compaction")
        print("串行化Session → 权限分级 → 记忆策展 → Skill热加载 → LLM推理 → Tool执行 → 上下文压缩")
        print(f"LLM: {self.llm}")
        print(f"Tools: {self.tool_executor}")
        print(f"Compaction: {self.compactor}")
        print("命令: quit=退出 / plan=Plan模式 / bypass=全放开 / default=标准权限 / compact=手动压缩 / status=查看状态")
        print("=" * 60)
        
        # 启动时显示记忆和Skill状态
        self._show_startup_info()
        
        while True:
            try:
                user_input = input("\n你: ").strip()
            except (EOFError, KeyboardInterrupt):
                print("\n退出")
                break
            
            # 特殊命令处理
            if user_input.lower() == "quit":
                self._on_session_end()
                print("退出")
                break
            
            if user_input.lower() == "plan":
                self.permissions = PermissionProfile(mode="plan")
                self.permissions.set_session("default")
                print("[权限] 切换到Plan模式 — 只允许只读操作")
                continue
            
            if user_input.lower() == "bypass":
                self.permissions = PermissionProfile(mode="bypass")
                self.permissions.set_session("default")
                print("[权限] 切换到Bypass模式 — 全放开（仅rm -rf拦截）")
                continue
            
            if user_input.lower() == "compact":
                history_msgs = self._session_history_to_messages(session)
                compacted_msgs, result = self.compactor.compact(history_msgs, trigger=CompactionTrigger.MANUAL)
                print(f"[Compaction] 手动压缩完成: {result}")
                self._replace_session_history(session, compacted_msgs)
                continue
            
            if user_input.lower() == "default":
                self.permissions = PermissionProfile(mode="default")
                self.permissions.set_session("default")
                print("[权限] 切换到Default模式 — 三层分级权限")
                continue
            
            if user_input.lower() == "status":
                self._show_status()
                continue
            
            if not user_input:
                continue
            
            # === 核心对话循环（9步） ===
            response = await self._process_message(session, user_input)
            
            # 输出回复
            print(f"\nAgent: {response.content}")
            
            # 如果有记忆写入提示
            if response.memory_writes:
                print(f"[记忆] 已写入: {response.memory_writes}")
    
    async def _process_message(self, session: Session, user_input: str) -> Response:
        """
        处理一条消息 — 9步对话循环
        
        这是Mini Harness的核心引擎
        """
        # Step 1: 消息入队（串行化）
        message = Message(content=user_input)
        await session.enqueue(message)
        
        # Step 2: 串行取出
        msg = await session.process_next()
        if msg is None:
            return Response(content="[系统] Session正在处理其他消息")
        
        # Step 3: 加载记忆
        memory_context = self._load_memory_context()
        
        # Step 4: Skill注入
        skill_prompt = self.skills.inject_skills_prompt(user_input)
        
        # Step 5: 权限评估 — 判断本次需要什么权限级别
        tool_level, action_desc = self._determine_tool_level(user_input)
        perm_decision = self.permissions.evaluate(tool_level, action_desc)
        
        # Step 6: 权限交互 — 如果需要批准，询问用户
        if perm_decision == Decision.DENY:
            session.finish_processing()
            return Response(
                content=f"[权限拒绝] {action_desc} — deny规则最高优先级，无法执行",
                tool_calls=[{"level": tool_level.value, "action": action_desc, "decision": "deny"}]
            )
        
        if perm_decision == Decision.ASK:
            approved = self._ask_user_approval(tool_level, action_desc)
            if not approved:
                session.finish_processing()
                return Response(
                    content=f"[权限拒绝] 用户拒绝了 {action_desc}",
                    tool_calls=[{"level": tool_level.value, "action": action_desc, "decision": "user_reject"}]
                )
        
        # Step 7: Agent处理 — 组装完整prompt，生成回复
        full_prompt = self._build_prompt(memory_context, skill_prompt, user_input)
        agent_response = self._agent_think(full_prompt, user_input)
        
        # Step 8: 记忆写入 — 重要内容写入记忆
        memory_writes = self._maybe_write_memory(user_input, agent_response)
        
        # Step 8b: Compaction检查 — session历史是否需要压缩
        history_msgs = self._session_history_to_messages(session)
        if self.compactor.should_compact(history_msgs):
            compacted_msgs, compact_result = self.compactor.compact(history_msgs, trigger=CompactionTrigger.AUTO)
            print(f"[Compaction] {compact_result}")
            # 用压缩后的历史替换session历史
            self._replace_session_history(session, compacted_msgs)
        
        # Step 9: 记录历史 + 释放串行锁
        response = Response(
            content=agent_response,
            tool_calls=[{"level": tool_level.value, "action": action_desc, "decision": perm_decision.value}],
            memory_writes=memory_writes,
        )
        session.add_to_history(msg, response)
        session.finish_processing()
        
        return response
    
    # ===== 各步骤的实现 =====
    
    def _session_history_to_messages(self, session: Session) -> list[dict]:
        """把session历史转换为LLM messages格式"""
        messages = []
        for hist in session.history:
            # 用户消息
            if hist.user_msg:
                messages.append({"role": "user", "content": hist.user_msg.content})
            # Agent回复
            if hist.agent_resp:
                messages.append({"role": "assistant", "content": hist.agent_resp.content})
        return messages
    
    def _replace_session_history(self, session: Session, compacted_msgs: list[dict]):
        """用压缩后的messages替换session历史"""
        # 把messages转回session历史格式
        new_history = []
        i = 0
        while i < len(compacted_msgs):
            msg = compacted_msgs[i]
            if msg["role"] == "system":
                # system消息是summary，存为特殊历史条目
                new_history.append((
                    Message(content="[对话摘要已压缩]", metadata={"compacted": True}),
                    Response(content=msg["content"], tool_calls=[], memory_writes=[])
                ))
                i += 1
            elif msg["role"] == "user":
                user_msg = Message(content=msg["content"])
                # 找对应的assistant回复
                if i + 1 < len(compacted_msgs) and compacted_msgs[i+1]["role"] == "assistant":
                    agent_resp = Response(content=compacted_msgs[i+1]["content"], tool_calls=[], memory_writes=[])
                    new_history.append((user_msg, agent_resp))
                    i += 2
                else:
                    new_history.append((user_msg, None))
                    i += 1
            else:
                i += 1
        
        session.history = new_history
    
    def _load_memory_context(self) -> str:
        """Step 3: 加载记忆上下文"""
        parts = []
        
        # 长期记忆（MEMORY.md）
        memory_content = self.memory.read_memory()
        if memory_content.strip():
            parts.append(f"## 长期记忆\n{memory_content}")
        
        # 今日日志
        today_log = self.memory.read_today_log()
        if today_log.strip():
            parts.append(f"## 今日日志\n{today_log[:500]}")  # 截取前500字符
        
        return "\n\n".join(parts) if parts else ""
    
    def _determine_tool_level(self, user_input: str) -> tuple[ToolLevel, str]:
        """
        Step 5: 根据用户输入判断需要什么权限级别
        
        简化规则：
        - 包含"读/看/查看/搜索" → Read-only
        - 包含"执行/运行/跑/bash/shell" → Shell
        - 包含"写/修改/编辑/创建/删除" → File modification
        - 其他 → Read-only（默认最低权限）
        """
        input_lower = user_input.lower()
        
        # Shell关键词
        shell_keywords = ["执行", "运行", "跑", "bash", "shell", "npm", "pip", "docker", "git"]
        for kw in shell_keywords:
            if kw in input_lower:
                return ToolLevel.SHELL, f"Shell命令: {user_input[:50]}"
        
        # File modification关键词
        write_keywords = ["写", "修改", "编辑", "创建", "删除", "新建", "更新"]
        for kw in write_keywords:
            if kw in input_lower:
                return ToolLevel.FILE_WRITE, f"文件修改: {user_input[:50]}"
        
        # 默认Read-only
        return ToolLevel.READ_ONLY, f"只读操作: {user_input[:50]}"
    
    def _ask_user_approval(self, tool_level: ToolLevel, action: str) -> bool:
        """Step 6b: 交互式询问用户是否批准"""
        scope_hint = ""
        if tool_level == ToolLevel.SHELL:
            scope_hint = "（批准后可选永久记住）"
        elif tool_level == ToolLevel.FILE_WRITE:
            scope_hint = "（批准后可选仅本次session记住）"
        
        print(f"\n[权限请求] {action} {scope_hint}")
        answer = input("是否批准? (y=批准 / n=拒绝 / r=批准并记住不再询问): ").strip().lower()
        
        if answer == "y":
            return True
        elif answer == "r":
            # "不再询问" — 记录批准
            self.permissions.approve(tool_level, action, action, remember=True)
            scope_name = "永久" if tool_level == ToolLevel.SHELL else "本次session"
            print(f"[权限] 已记住批准 {scope_name}")
            return True
        else:
            return False
    
    def _build_prompt(self, memory_context: str, skill_prompt: str, user_input: str) -> str:
        """Step 7a: 组装完整prompt"""
        parts = [
            "# Mini Harness Agent Prompt",
        ]
        
        if memory_context:
            parts.append(memory_context)
        
        if skill_prompt:
            parts.append(skill_prompt)
        
        parts.append(f"\n## 用户输入\n{user_input}")
        
        return "\n\n".join(parts)
    
    def _agent_think(self, full_prompt: str, user_input: str) -> str:
        """
        Step 7b: Agent推理 — LLM+Tool ReAct循环
        
        流程：
        1. 组装messages（system prompt + 记忆 + Skill + 用户输入 + Tool描述）
        2. ToolExecutor.run_tool_loop() — LLM推理→Tool调用→观察→继续推理
        3. 最大5轮Tool调用，防止无限循环
        """
        # 组装messages
        messages = [
            {
                "role": "system",
                "content": f"你是Mini Harness Agent，一个技术助手。你有记忆系统可以记住重要信息，有Skill系统可以自动注入专业知识，有Tool系统可以真正执行操作（读文件、写文件、跑命令、搜代码）。回答要简洁、有观点、不废话。当前时间: {datetime.now().strftime('%Y-%m-%d %A %H:%M')}（北京时间）。"
            },
        ]
        
        # 记忆上下文
        memory_context = self._load_memory_context()
        if memory_context:
            messages.append({"role": "system", "content": f"## 你的记忆\n{memory_context}"})
        
        # Skill注入
        skill_prompt = self.skills.inject_skills_prompt(user_input)
        if skill_prompt:
            messages.append({"role": "system", "content": skill_prompt})
        
        # 用户输入
        messages.append({"role": "user", "content": user_input})
        
        # Tool描述注入（告诉LLM有哪些Tool可以用）
        tool_descriptions = []
        for tool in self.tool_registry.list_all():
            tool_descriptions.append(f"- {tool.name}: {tool.description} (权限级别: {tool.tool_level})")
        if tool_descriptions:
            messages.append({"role": "system", "content": "## 可用工具\n" + "\n".join(tool_descriptions)})
        
        # ReAct循环：LLM推理→Tool调用→观察→继续推理
        def llm_chat_func(msgs, tools=None):
            return self.llm.chat_raw(msgs, max_tokens=500, tools=tools)
        
        final_text, _ = self.tool_executor.run_tool_loop(llm_chat_func, messages)
        
        return final_text
    
    def _maybe_write_memory(self, user_input: str, response: str) -> list[str]:
        """
        Step 8: 判断是否需要写入记忆
        
        简化逻辑：
        - 如果用户说了重要结论/决策 → 写入MEMORY.md
        - 每次对话都记录日志
        """
        writes = []
        
        # 简化判断：用户输入包含"记住/记住这个/结论/决定"时写入长期记忆
        memory_keywords = ["记住", "结论", "决定", "决策", "教训", "踩坑"]
        should_write = any(kw in user_input.lower() for kw in memory_keywords)
        
        if should_write:
            # 判断分类
            category = "lesson" if "踩坑" in user_input or "教训" in user_input else "decision"
            entry = MemoryEntry(
                category=category,
                content=user_input,
                project="mini_harness",
            )
            success = self.memory.add_to_memory(entry)
            if success:
                writes.append(f"MEMORY.md [{category}]")
        
        # 写日志（每次都写）
        log = DailyLog(
            project="mini_harness",
            title=user_input[:30],
            conclusion=response[:50],
            files_changed=[],
            tags=["interactive"],
        )
        self.memory.write_daily_log(log)
        writes.append("日志")
        
        return writes
    
    # ===== 辅助方法 =====
    
    def _show_startup_info(self):
        """启动时显示Agent状态"""
        print(f"\n[权限模式] {self.permissions}")
        print(f"[记忆状态] {self.memory}")
        print(f"[Skill状态] {self.skills}")
        print(f"[LLM模式] {self.llm}")
        print(f"[Tool系统] {self.tool_executor}")
        print(f"[Compaction] {self.compactor}")
        skill_list = self.skills.list_skills()
        if skill_list:
            print(f"  可用Skill: {[s['name'] for s in skill_list]}")
        tool_list = self.tool_registry.list_all()
        if tool_list:
            print(f"  可用Tool: {[(t.name, t.tool_level) for t in tool_list]}")
        if self.llm._use_echo:
            print("\n提示: 设置环境变量后可接入真实LLM:")
            print("  export LLM_API_KEY=your-key")
            print("  export LLM_BASE_URL=https://api.openai.com/v1  # 或中转站")
            print("  export LLM_MODEL=gpt-4o-mini")
    
    def _show_status(self):
        """显示当前状态"""
        print(f"\n--- Mini Harness 状态 ---")
        print(f"[权限] {self.permissions}")
        print(f"[记忆] {self.memory}")
        print(f"[Skill] {self.skills}")
        print(f"[Session] {self.session_mgr.list_active_sessions()}")
        print(f"[Compaction] {self.compactor}")
        
        cap = self.memory.memory_capacity_info()
        print(f"[MEMORY.md容量] {cap['current_chars']}/{cap['max_chars']} ({cap['usage_pct']}%)")
        
        # 压缩历史
        hist = self.compactor.get_history()
        if hist:
            print(f"[压缩历史] 已压缩{len(hist)}次")
            print(f"  最近: {hist[-1]}")
        else:
            print("[压缩历史] 未压缩")
    
    def _on_session_end(self):
        """Session结束时清除session级记忆"""
        self.permissions.clear_session_records()
        print("[权限] Session结束，已清除session级批准记录")


# ===== 入口 =====

if __name__ == "__main__":
    # 默认使用SiliconFlow + GLM-5.1
    api_key = os.environ.get("LLM_API_KEY", "")
    base_url = os.environ.get("LLM_BASE_URL", "https://api.siliconflow.cn/v1")
    model = os.environ.get("LLM_MODEL", "Pro/zai-org/GLM-5.1")

    harness = MiniHarness(
        memory_dir="memory_dir",
        skill_dirs={"bundled": "skills_dir"},
        permission_mode="default",
        llm_api_key=api_key,
        llm_base_url=base_url,
        llm_model=model,
    )
    asyncio.run(harness.run_interactive())