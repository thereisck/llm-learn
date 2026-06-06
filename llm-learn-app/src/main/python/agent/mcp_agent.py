"""MCP Client Agent：通过MCP协议调用文件系统工具"""
import json
import asyncio
from contextlib import AsyncExitStack
from openai import OpenAI
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
# --- MCP Client连接 ---
class MCPClient:
    def __init__(self):
        self.session = None
        self.exit_stack = AsyncExitStack()
        self.available_tools = []  # Server自描述发现的工具列表
    
    async def connect(self, server_script: str):
        """连接MCP Server（stdio模式）"""
        server_params = StdioServerParameters(
            command="/opt/homebrew/opt/python@3.11/bin/python3.11",
            args=[server_script],
        )
        
        stdio_transport = await self.exit_stack.enter_async_context(
            stdio_client(server_params)
        )
        read_stream, write_stream = stdio_transport
        
        self.session = await self.exit_stack.enter_async_context(
            ClientSession(read_stream, write_stream)
        )
        
        # 初始化连接，获取Server自描述的工具列表
        await self.session.initialize()
        tools_result = await self.session.list_tools()
        
        self.available_tools = [
            {
                "type": "function",
                "function": {
                    "name": tool.name,
                    "description": tool.description,
                    "parameters": tool.inputSchema,
                }
            }
            for tool in tools_result.tools
        ]
        
        # 打印Server暴露的工具（观察自描述）
        print(f"[MCP发现] Server暴露了 {len(tools_result.tools)} 个工具：")
        for tool in tools_result.tools:
            print(f"  - {tool.name}: {tool.description}")
    
    async def call_tool(self, tool_name: str, args: dict) -> str:
        """通过MCP协议调用Server的工具"""
        result = await self.session.call_tool(tool_name, args)
        return str(result.content[0].text) if result.content else "无返回"
    
    async def cleanup(self):
        await self.exit_stack.aclose()
        
# --- Agent主循环 ---
SILICONFLOW_API_KEY = os.environ.get("SILICONFLOW_API_KEY", "")
SILICONFLOW_BASE_URL = "https://api.siliconflow.cn/v1"
MODEL = "Pro/zai-org/GLM-5.1"

client = OpenAI(api_key=SILICONFLOW_API_KEY, base_url=SILICONFLOW_BASE_URL)

async def chat_with_mcp(user_message: str, mcp_client: MCPClient) -> str:
    messages = [{"role": "user", "content": user_message}]
    
    response = client.chat.completions.create(
        model=MODEL,
        messages=messages,
        tools=mcp_client.available_tools,
    )
    
    choice = response.choices[0]
    message = choice.message
    
    if not message.tool_calls:
        return message.content
    
    messages.append(message)
    
    for tool_call in message.tool_calls:
        func_name = tool_call.function.name
        func_args = json.loads(tool_call.function.arguments)
        
        print(f"[MCP工具调用] {func_name}({func_args})")
        
        # 通过MCP协议调Server的工具（不是本地直接调！）
        result = await mcp_client.call_tool(func_name, func_args)
        
        print(f"[MCP返回] {result[:200]}...")
        
        messages.append({
            "role": "tool",
            "tool_call_id": tool_call.id,
            "content": result,
        })
    
    response2 = client.chat.completions.create(
        model=MODEL,
        messages=messages,
        tools=mcp_client.available_tools,
    )
    
    return response2.choices[0].message.content

async def main():
    mcp_client = MCPClient()
    
    # 连接MCP Server
    await mcp_client.connect(
        "/Users/zhiweizhang/Downloads/aicc/workspace/llm-learn/llm-learn-app/src/main/python/agent/mcp_server.py"
    )
    
    # 测试
    questions = [
        "列出当前agent目录下有哪些文件",
        "读取weather_agent.py的内容",
    ]
    
    for q in questions:
        print(f"\n提问: {q}")
        answer = await chat_with_mcp(q, mcp_client)
        print(f"回答: {answer}")
    
    await mcp_client.cleanup()

if __name__ == "__main__":
    asyncio.run(main())