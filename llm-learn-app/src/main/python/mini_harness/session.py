"""
Session管理模块 — 核心设计原则：串行化对话循环

参考：
- OpenClaw per-session 队列（同一 session 串行，不同 session 并行）
- Codex realtime_conversation（消息排队 + 全局并发上限）

设计要点：
1. 每个 Session 拥有独立的 asyncio.Queue，保证同一 Session 内消息严格串行处理，避免竞态
2. 不同 Session 之间可以并行，但受全局 Semaphore 限制并发数
3. is_processing 标志位充当「本地串行锁」，Semaphore 充当「全局并发锁」
"""
import asyncio
import time
import uuid
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class Message:
    """用户侧消息实体
    参考 OpenClaw inbound event 结构，精简为 id + content + timestamp
    id 取 uuid 前8位，足以在单 session 内去重
    """
    id: str = field(default_factory=lambda: str(uuid.uuid4())[:8])
    content: str = ""
    timestamp: float = field(default_factory=time.time)
    sender: str = "user"


@dataclass
class Response:
    """Agent 侧回复实体
    参考 OpenClaw outbound event 结构，增加 tool_calls 和 memory_writes
    用于追踪 Agent 的工具调用和记忆写入，便于审计和回溯
    """
    id: str = field(default_factory=lambda: str(uuid.uuid4())[:8])
    content: str = ""
    timestamp: float = field(default_factory=time.time)
    tool_calls: list = field(default_factory=list)  # 工具调用记录
    memory_writes: list = field(default_factory=list)  # 记忆写入记录


class Session:
    """
    一个独立的对话Session
    
    设计原则：
    - 串行化：同一Session的消息排队处理，不会两条消息同时跑
    - 隔离：每个Session有独立的context、记忆、权限状态
    """
    
    def __init__(self, session_id: str, max_concurrent_sessions: int = 5):
        """初始化 Session
        参考 OpenClaw Gateway：每个 session 绑定一个全局 Semaphore
        Semaphore 跨所有 Session 共享同一引用，控制同时处理的 session 总数
        默认 max_concurrent=5，平衡吞吐与资源消耗
        """
        self.id = session_id
        self.queue: asyncio.Queue = asyncio.Queue()      # 串行消息队列，FIFO
        self.history: list[dict] = []                      # 对话历史，用于组装 prompt
        self.created_at = time.time()
        self.is_processing = False                         # 本地串行锁：同一 session 不并发
        
        # 全局并发锁 — 所有 Session 实例共享同一 Semaphore 引用
        # 参考 OpenClaw 全局并发上限设计
        self._global_semaphore = asyncio.Semaphore(max_concurrent_sessions)
    
    async def enqueue(self, message: Message):
        """消息入队 — 非阻塞式排队
        参考 OpenClaw inbound handler：消息先入 Queue 再异步消费
        Queue 无上限，防止高并发时丢消息
        """
        print(f"[Session {self.id}] 收到消息: {message.content[:50]}...")
        await self.queue.put(message)
    
    async def process_next(self) -> Optional[Message]:
        """
        从队列取一条消息 — 串行化 + 全局并发的双重保障
        
        两层锁机制：
        1. is_processing 标志位：同一 Session 内串行，正在处理时拒绝取新消息
        2. Semaphore：跨 Session 全局并发上限，防止所有 Session 同时狂跑
        
        参考 OpenClaw per-session queue + 全局 Semaphore 设计
        """
        if self.is_processing:
            return None
        
        async with self._global_semaphore:
            self.is_processing = True
            try:
                message = await self.queue.get()
                return message
            except asyncio.CancelledError:
                self.is_processing = False
                return None
    
    def finish_processing(self):
        """处理完毕，释放本地串行锁
        必须在 Agent 完成一轮回复后调用，否则该 Session 永远卡住
        注意：Semaphore 在 process_next 的 async with 里自动释放，无需手动
        """
        self.is_processing = False
    
    def add_to_history(self, message: Message, response: Response):
        """追加一轮对话到历史
        参考 OpenClaw conversation log：保留完整的 (message, response) 对
        后续 get_context 从这里截取最近 N 轮组装 prompt
        """
        self.history.append({
            "message": message,
            "response": response,
        })
    
    def get_context(self, max_messages: int = 10) -> list[dict]:
        """截取最近 N 轮对话上下文，组装为 OpenAI chat format
        参考 OpenClaw context window 截断策略：只取最近 max_messages 轮
        输出格式：[{role: user/assistant, content: ...}]，兼容 OpenAI API
        注意：当前实现是 user 全部在前、assistant 全部在后，后续应改为交替排列
        """
        recent = self.history[-max_messages:]
        return [
            {"role": "user", "content": m["message"].content}
            for m in recent
        ] + [
            {"role": "assistant", "content": m["response"].content}
            for m in recent
        ]
    
    def __repr__(self):
        return f"Session(id={self.id}, msgs={self.queue.qsize()}, processing={self.is_processing})"


class SessionManager:
    """
    Session管理器 — 类似OpenClaw的Gateway
    
    负责：
    - 创建/查找Session
    - 消息路由（DM共享 vs group隔离）
    - 全局并发上限
    """
    
    def __init__(self, max_concurrent: int = 5):
        """初始化 Session 管理器
        参考 OpenClaw Gateway：统一管理所有 session 的创建、查找、路由
        max_concurrent 传给每个 Session 的 Semaphore，控制全局并发上限
        """
        self.sessions: dict[str, Session] = {}
        self.max_concurrent = max_concurrent
    
    def create_session(self, session_id: Optional[str] = None) -> Session:
        """创建新 Session，若 id 已存在则直接返回（幂等）
        参考 OpenClaw session 生命周期：首次消息自动创建，不会重复创建
        session_id 缺省时自动生成 uuid 前8位
        """
        if session_id is None:
            session_id = str(uuid.uuid4())[:8]
        
        if session_id in self.sessions:
            return self.sessions[session_id]
        
        session = Session(session_id, self.max_concurrent)
        self.sessions[session_id] = session
        print(f"[SessionManager] 创建Session: {session_id}")
        return session
    
    def get_session(self, session_id: str) -> Optional[Session]:
        """按 id 查找 Session，不存在返回 None
        不自动创建，与 create_session 分离，避免隐式副作用
        """
        return self.sessions.get(session_id)
    
    def list_active_sessions(self) -> list[Session]:
        """列出所有已注册的 Session（含空闲和处理中的）
        参考 OpenClaw Gateway status：用于监控和调试
        """
        return list(self.sessions.values())
    
    async def route_message(self, session_id: str, content: str) -> Message:
        """
        消息路由 — 把用户消息分发到对应 Session
        
        OpenClaw 的 DM 隔离模式：
        - per-peer：每个用户一个 Session（DM 场景下所有频道共享上下文）
        - per-channel-peer：每个频道+用户组合一个 Session（群聊隔离上下文）
        本实现简化为 per-session_id：一个 id 对应一个 Session
        
        若 Session 不存在则自动创建（懒初始化，参考 OpenClaw inbound handler）
        """
        session = self.get_session(session_id)
        if session is None:
            session = self.create_session(session_id)
        
        message = Message(content=content)
        await session.enqueue(message)
        return message


# ===== 测试代码 =====
async def test_session():
    """测试 Session 管理的串行化机制
    验证：多 Session 并行入队 → 串行消费 → is_processing 状态切换
    """
    manager = SessionManager(max_concurrent=3)
    
    # 模拟3个不同用户发消息
    s1 = manager.create_session("user_a")
    s2 = manager.create_session("user_b")
    s3 = manager.create_session("user_c")
    
    # 各Session入队2条消息
    await s1.enqueue(Message(content="你好，我是用户A"))
    await s1.enqueue(Message(content="继续聊"))
    await s2.enqueue(Message(content="用户B来了"))
    await s3.enqueue(Message(content="用户C也来了"))
    
    print(f"\n活跃Session: {manager.list_active_sessions()}")
    print(f"S1队列: {s1.queue.qsize()} 条消息")
    print(f"S2队列: {s2.queue.qsize()} 条消息")
    print(f"S3队列: {s3.queue.qsize()} 条消息")
    
    # 模拟串行处理
    msg = await s1.process_next()
    print(f"\nS1处理消息: {msg.content}")
    s1.finish_processing()
    
    msg2 = await s1.process_next()
    print(f"S1处理第二条: {msg2.content}")
    s1.finish_processing()


if __name__ == "__main__":
    asyncio.run(test_session())