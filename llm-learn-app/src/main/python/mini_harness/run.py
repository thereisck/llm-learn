"""
Mini Harness 入口 — 启动Agent对话循环

先跑起来验证Session模块，后续逐步添加权限、记忆、Skill模块
"""
import asyncio
from session import SessionManager, Message, Response


async def interactive_chat():
    """交互式聊天 — 模拟Agent对话循环"""
    manager = SessionManager(max_concurrent=3)
    session = manager.create_session("default")
    
    print("=" * 50)
    print("Mini Harness v0.1 — 串行化对话循环")
    print("核心设计原则: per-session队列防止竞态")
    print("输入 'quit' 退出")
    print("=" * 50)
    
    while True:
        try:
            user_input = input("\n你: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n退出")
            break
        
        if user_input.lower() == "quit":
            print("退出")
            break
        
        if not user_input:
            continue
        
        # Step 1: 消息入队（串行化）
        message = Message(content=user_input)
        await session.enqueue(message)
        
        # Step 2: 从队列取出（同一session同时只处理一条）
        msg = await session.process_next()
        if msg is None:
            print("[系统] Session正在处理其他消息，请稍候")
            continue
        
        # Step 3: Agent处理（目前只是echo，后续接入LLM）
        print(f"[Session] 正在处理: {msg.content}")
        response = Response(content=f"[Echo] 收到: {msg.content}")
        
        # Step 4: 记录历史
        session.add_to_history(msg, response)
        
        # Step 5: 输出
        print(f"Agent: {response.content}")
        
        # Step 6: 释放串行锁
        session.finish_processing()
        
        # 显示Session状态
        print(f"[状态] {session}")


if __name__ == "__main__":
    asyncio.run(interactive_chat())