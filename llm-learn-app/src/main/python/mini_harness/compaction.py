"""
上下文压缩（Compaction）— 防session历史无限膨胀

参考四大Agent的Compaction设计：
- OpenClaw: token接近上限时自动压缩历史为summary，保留最近消息完整
- Claude Code: /compact手动触发 + 自动compaction（接近context上限时触发）
- Codex: compact.rs有CompactionStrategy/CompactionTrigger/CompactionReason枚举
  + Pre/Post Compaction Hooks（压缩前后可执行自定义脚本）

设计原则：
1. 触发条件：token数接近上限（默认70%阈值）→ 自动触发
2. 压缩策略：保留最近N条消息完整，旧消息压缩为LLM生成的summary
3. 手动触发：用户可以输入 /compact 强制压缩
4. Pre/Post Hooks：压缩前保存快照，压缩后记录日志
"""
import json
from datetime import datetime
from dataclasses import dataclass, field
from typing import Optional, Callable


@dataclass
class CompactionResult:
    """压缩结果"""
    original_count: int      # 原始消息数
    original_tokens: int     # 原始token估算
    compacted_count: int     # 压缩后消息数
    compacted_tokens: int    # 压缩后token估算
    summary: str             # 压缩生成的摘要
    kept_messages: int       # 保留的最近消息数
    triggered_by: str        # 触发方式: auto/manual/threshold
    
    def __str__(self):
        ratio = (1 - self.compacted_tokens / self.original_tokens) * 100 if self.original_tokens > 0 else 0
        return f"压缩: {self.original_count}条({self.original_tokens}t) → {self.compacted_count}条({self.compacted_tokens}t), 省了{ratio:.0f}%"


class CompactionStrategy:
    """
    压缩策略枚举（参考Codex compact.rs）
    
    - KEEP_RECENT: 保留最近N条，旧消息压缩为summary
    - SUMMARIZE_ALL: 全部压缩为一条summary
    - SLIDING_WINDOW: 滑动窗口，保留最近N条+每隔K条保留1条
    """
    KEEP_RECENT = "keep_recent"
    SUMMARIZE_ALL = "summarize_all"
    SLIDING_WINDOW = "sliding_window"


@dataclass
class CompactionTrigger:
    """
    压缩触发条件（参考Codex CompactionTrigger）
    
    - TOKEN_THRESHOLD: token数超过阈值
    - MESSAGE_COUNT: 消息数超过阈值
    - MANUAL: 用户手动触发(/compact)
    - AUTO: 自动触发(每次对话后检查)
    """
    TOKEN_THRESHOLD = "token_threshold"
    MESSAGE_COUNT = "message_count"
    MANUAL = "manual"
    AUTO = "auto"


@dataclass 
class CompactionReason:
    """
    压缩原因（参考Codex CompactionReason）
    """
    CONTEXT_TOO_LONG = "context_too_long"
    USER_REQUESTED = "user_requested"
    PERIODIC = "periodic"


class Compactor:
    """
    上下文压缩器
    
    核心功能：
    1. 估算messages的token数（简单估算：中文≈2token/字，英文≈1.5token/字）
    2. 判断是否需要压缩（token数超过阈值百分比）
    3. 执行压缩：保留最近N条 + 旧消息压缩为LLM生成的summary
    4. Pre/Post Hooks
    """
    
    # 默认配置
    DEFAULT_MAX_TOKENS = 4000      # GLM-5.1约4K context（保守估计）
    DEFAULT_THRESHOLD_PCT = 0.70   # 70%时触发压缩
    DEFAULT_KEEP_RECENT = 6        # 保留最近6条消息（3轮对话）
    DEFAULT_STRATEGY = CompactionStrategy.KEEP_RECENT
    
    def __init__(
        self,
        max_tokens: int = DEFAULT_MAX_TOKENS,
        threshold_pct: float = DEFAULT_THRESHOLD_PCT,
        keep_recent: int = DEFAULT_KEEP_RECENT,
        strategy: str = DEFAULT_STRATEGY,
        llm_client=None,  # LLMClient实例，用于生成summary
    ):
        self.max_tokens = max_tokens
        self.threshold_pct = threshold_pct
        self.keep_recent = keep_recent
        self.strategy = strategy
        self.llm = llm_client
        
        # Pre/Post Hooks
        self._pre_hooks: list[Callable] = []
        self._post_hooks: list[Callable] = []
        
        # 压缩历史（记录每次压缩）
        self._history: list[CompactionResult] = []
    
    def estimate_tokens(self, messages: list[dict]) -> int:
        """
        估算messages的token数
        
        简单估算规则：
        - 中文字符 ≈ 2 tokens/字
        - 英文字符 ≈ 1.5 tokens/字（按空格分词）
        - JSON/代码 ≈ 2 tokens/字
        
        这不是精确计算，但足够判断是否需要压缩
        """
        total = 0
        for msg in messages:
            content = msg.get("content", "") or ""
            if not content:
                continue
            
            # 检测内容类型
            chinese_chars = sum(1 for c in content if '\u4e00' <= c <= '\u9fff')
            other_chars = len(content) - chinese_chars
            
            # 估算
            total += chinese_chars * 2 + other_chars * 1.5
        
        return int(total)
    
    def should_compact(self, messages: list[dict], trigger: str = CompactionTrigger.AUTO) -> bool:
        """
        判断是否需要压缩
        
        条件：token数 > max_tokens * threshold_pct
        或者 消息数 > 30
        """
        token_count = self.estimate_tokens(messages)
        msg_count = len(messages)
        
        if trigger == CompactionTrigger.MANUAL:
            return True
        
        if token_count > self.max_tokens * self.threshold_pct:
            return True
        
        if msg_count > 30:
            return True
        
        return False
    
    def compact(self, messages: list[dict], trigger: str = CompactionTrigger.AUTO) -> tuple[list[dict], CompactionResult]:
        """
        执行压缩
        
        返回：(压缩后的messages, CompactionResult)
        
        策略KEEP_RECENT：
        1. 保留最近keep_recent条消息
        2. 旧消息交给LLM生成summary
        3. summary作为一条system消息插入
        
        策略SUMMARIZE_ALL：
        1. 全部消息交给LLM生成summary
        2. 只保留summary + 最近2条
        """
        # Pre Hooks
        for hook in self._pre_hooks:
            hook(messages)
        
        original_tokens = self.estimate_tokens(messages)
        original_count = len(messages)
        
        if self.strategy == CompactionStrategy.KEEP_RECENT:
            compacted_messages, summary = self._compact_keep_recent(messages)
        elif self.strategy == CompactionStrategy.SUMMARIZE_ALL:
            compacted_messages, summary = self._compact_summarize_all(messages)
        else:
            compacted_messages, summary = self._compact_keep_recent(messages)
        
        compacted_tokens = self.estimate_tokens(compacted_messages)
        compacted_count = len(compacted_messages)
        kept = self.keep_recent if self.strategy == CompactionStrategy.KEEP_RECENT else 2
        
        result = CompactionResult(
            original_count=original_count,
            original_tokens=original_tokens,
            compacted_count=compacted_count,
            compacted_tokens=compacted_tokens,
            summary=summary,
            kept_messages=kept,
            triggered_by=trigger,
        )
        
        self._history.append(result)
        
        # Post Hooks
        for hook in self._post_hooks:
            hook(compacted_messages, result)
        
        return compacted_messages, result
    
    def _compact_keep_recent(self, messages: list[dict]) -> tuple[list[dict], str]:
        """
        KEEP_RECENT策略：保留最近N条，旧消息压缩为summary
        """
        if len(messages) <= self.keep_recent:
            return messages, ""  # 消息太少，不需要压缩
        
        # 分割：旧消息 + 最近消息
        old_messages = messages[:-self.keep_recent]
        recent_messages = messages[-self.keep_recent:]
        
        # 生成旧消息的summary
        summary = self._generate_summary(old_messages)
        
        # 构造压缩后的messages
        compacted = []
        if summary:
            compacted.append({
                "role": "system",
                "content": f"## 之前对话的摘要\n{summary}\n\n(以上是之前对话的压缩摘要，最近{self.keep_recent}条消息保持完整)"
            })
        compacted.extend(recent_messages)
        
        return compacted, summary
    
    def _compact_summarize_all(self, messages: list[dict]) -> tuple[list[dict], str]:
        """
        SUMMARIZE_ALL策略：全部压缩为summary，只保留最近2条
        """
        if len(messages) <= 4:
            return messages, ""
        
        recent = messages[-2:]
        all_content = messages[:-2]
        
        summary = self._generate_summary(all_content)
        
        compacted = []
        if summary:
            compacted.append({
                "role": "system",
                "content": f"## 对话摘要\n{summary}\n\n(这是完整对话的压缩摘要)"
            })
        compacted.extend(recent)
        
        return compacted, summary
    
    def _generate_summary(self, messages: list[dict]) -> str:
        """
        生成messages的summary
        
        如果有LLM → 用LLM生成高质量summary
        如果没有LLM → 用简单规则生成摘要
        """
        if self.llm and not self.llm._use_echo:
            return self._llm_summary(messages)
        else:
            return self._simple_summary(messages)
    
    def _llm_summary(self, messages: list[dict]) -> str:
        """用LLM生成高质量summary"""
        # 把旧消息拼接成文本
        conversation_text = ""
        for msg in messages:
            role = msg.get("role", "unknown")
            content = msg.get("content", "") or ""
            if content:
                conversation_text += f"[{role}]: {content}\n\n"
        
        # 截断超长文本（LLM也有context限制）
        if len(conversation_text) > 3000:
            conversation_text = conversation_text[:3000] + "\n... (截断)"
        
        summary_messages = [
            {"role": "system", "content": "你是摘要助手。请把以下对话压缩为简洁的中文摘要，保留关键结论、决策和重要信息，去掉冗余和重复。摘要不超过500字。"},
            {"role": "user", "content": f"请压缩以下对话:\n\n{conversation_text}"},
        ]
        
        summary = self.llm.chat(summary_messages, max_tokens=300)
        return summary
    
    def _simple_summary(self, messages: list[dict]) -> str:
        """简单规则生成摘要（无LLM时的fallback）"""
        summary_parts = []
        user_msgs = [m for m in messages if m.get("role") == "user"]
        assistant_msgs = [m for m in messages if m.get("role") == "assistant"]
        
        summary_parts.append(f"共{len(messages)}条消息({len(user_msgs)}条用户消息+{len(assistant_msgs)}条回复)")
        
        # 提取用户消息的关键词
        for msg in user_msgs[:5]:
            content = (msg.get("content", "") or "")[:100]
            if content:
                summary_parts.append(f"- 用户问: {content}")
        
        # 提取assistant回复的结论
        for msg in assistant_msgs[:5]:
            content = (msg.get("content", "") or "")[:100]
            if content:
                summary_parts.append(f"- Agent答: {content}")
        
        return "\n".join(summary_parts)
    
    def add_pre_hook(self, hook: Callable):
        """添加Pre Hook（压缩前执行）"""
        self._pre_hooks.append(hook)
    
    def add_post_hook(self, hook: Callable):
        """添加Post Hook（压缩后执行）"""
        self._post_hooks.append(hook)
    
    def get_history(self) -> list[CompactionResult]:
        """获取压缩历史"""
        return self._history
    
    def get_status(self) -> dict:
        """获取压缩器状态"""
        return {
            "max_tokens": self.max_tokens,
            "threshold_pct": self.threshold_pct,
            "keep_recent": self.keep_recent,
            "strategy": self.strategy,
            "compact_count": len(self._history),
            "last_compact": self._history[-1] if self._history else None,
        }
    
    def __repr__(self):
        return f"Compactor(max={self.max_tokens}t, threshold={self.threshold_pct*100:.0f}%, keep={self.keep_recent}, strategy={self.strategy})"