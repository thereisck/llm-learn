"""
Tool调用循环 — 让LLM真正能调用Tool干活

核心流程（参考四大Agent的ReAct循环）：
1. 把Tool schemas传给LLM（function calling格式）
2. LLM决定是否调用Tool → 返回tool_calls
3. 解析tool_calls → 执行Tool → 得到observation
4. 把observation回传给LLM → LLM基于结果继续推理
5. 重复2-4直到LLM不再调用Tool → 输出最终回复

这就是经典的 ReAct 循环：
Reasoning(推理) → Action(调用Tool) → Observation(观察结果) → 继续推理

参考来源：
- OpenClaw: agentCommand → tool execution → result injection
- Claude Code: tool_use → tool_result → 继续对话
- Codex: Responses API + tool calls + function calling
"""
import json
from typing import Optional

from tools import BaseTool, ToolResult, ToolRegistry


class ToolExecutor:
    """
    Tool执行器 — 解析LLM的tool调用 + 执行 + 结果回传
    
    LLM调用Tool有两种格式：
    1. OpenAI function calling格式（tool_calls数组）— 标准格式
    2. 纯文本格式（LLM在回复里写JSON）— fallback格式
    
    优先解析标准格式，fallback到文本格式
    """
    
    MAX_TOOL_LOOPS = 5  # 最大Tool调用轮数（防止无限循环）
    
    def __init__(self, registry: ToolRegistry):
        self.registry = registry
    
    def parse_tool_calls(self, llm_response: dict) -> list[dict]:
        """
        从LLM的API响应中解析tool_calls
        
        OpenAI function calling格式：
        {
            "choices": [{
                "message": {
                    "tool_calls": [{
                        "id": "call_xxx",
                        "function": {
                            "name": "read_file",
                            "arguments": "{\"path\": \"session.py\"}"
                        }
                    }]
                }
            }]
        }
        """
        # 标准格式：tool_calls数组
        message = llm_response.get("choices", [{}])[0].get("message", {})
        tool_calls = message.get("tool_calls", [])
        
        if tool_calls:
            result = []
            for tc in tool_calls:
                func = tc.get("function", {})
                name = func.get("name", "")
                arguments = func.get("arguments", "{}")
                
                # arguments可能是JSON字符串，需要解析
                if isinstance(arguments, str):
                    try:
                        arguments = json.loads(arguments)
                    except json.JSONDecodeError:
                        arguments = {}
                
                result.append({
                    "id": tc.get("id", ""),
                    "name": name,
                    "arguments": arguments,
                })
            return result
        
        return []
    
    def parse_text_tool_call(self, llm_text: str) -> Optional[dict]:
        """
        fallback：从LLM纯文本回复中解析Tool调用
        
        LLM有时不通过function calling而是直接在文本里写：
        ```json
        {"tool": "read_file", "arguments": {"path": "session.py"}}
        ```
        
        或者：
        [调用工具: read_file, 参数: path=session.py]
        """
        # 尝试解析JSON格式
        if "```json" in llm_text:
            try:
                start = llm_text.index("```json") + 7
                end = llm_text.index("```", start)
                json_str = llm_text[start:end].strip()
                parsed = json.loads(json_str)
                if "tool" in parsed or "name" in parsed:
                    name = parsed.get("tool") or parsed.get("name")
                    args = parsed.get("arguments") or parsed.get("args") or {}
                    return {"id": "text_call", "name": name, "arguments": args}
            except (ValueError, json.JSONDecodeError):
                pass
        
        # 尝试解析 [调用工具: xxx] 格式
        if "[调用工具:" in llm_text or "[call tool:" in llm_text:
            try:
                start = llm_text.index("[调用工具:") if "[调用工具:" in llm_text else llm_text.index("[call tool:")
                end = llm_text.index("]", start)
                call_text = llm_text[start:end+1]
                
                # 简单解析
                name = ""
                args = {}
                
                # 提取工具名
                parts = call_text.split(",")
                for part in parts:
                    if "调用工具:" in part or "call tool:" in part:
                        name = part.split(":")[-1].strip()
                    elif "参数:" in part or "args:" in part:
                        arg_str = part.split(":")[-1].strip()
                        # 参数格式: key=value
                        for kv in arg_str.split():
                            if "=" in kv:
                                k, v = kv.split("=", 1)
                                args[k.strip()] = v.strip()
                
                if name:
                    return {"id": "text_call", "name": name, "arguments": args}
            except ValueError:
                pass
        
        return None
    
    def execute_tool_call(self, tool_call: dict) -> dict:
        """
        执行单个Tool调用
        
        返回格式（OpenAI tool_result格式）：
        {
            "tool_call_id": "call_xxx",
            "role": "tool",
            "content": "工具执行结果..."
        }
        """
        name = tool_call["name"]
        arguments = tool_call["arguments"]
        
        tool = self.registry.get(name)
        if not tool:
            return {
                "tool_call_id": tool_call["id"],
                "role": "tool",
                "content": f"未知工具: {name}。可用工具: {[t.name for t in self.registry.list_all()]}"
            }
        
        # 执行Tool
        result: ToolResult = tool.execute(**arguments)
        
        return {
            "tool_call_id": tool_call["id"],
            "role": "tool",
            "content": result.to_llm_message(),
        }
    
    def run_tool_loop(
        self,
        llm_chat_func,
        messages: list[dict],
    ) -> tuple[str, list[dict]]:
        """
        ReAct循环 — LLM推理→Tool调用→观察结果→继续推理
        
        参数：
        - llm_chat_func: LLM的chat函数（传入messages+tools，返回完整API响应dict）
        - messages: 对话历史
        
        返回：
        - final_text: LLM最终回复文本
        - messages: 更新后的对话历史（包含tool调用和结果）
        
        循环最多5轮Tool调用，防止无限循环
        """
        tool_schemas = self.registry.get_schemas()
        
        for loop_count in range(self.MAX_TOOL_LOOPS):
            # 调用LLM（带Tool schemas）
            llm_response = llm_chat_func(messages, tools=tool_schemas)
            
            # 解析回复
            message = llm_response.get("choices", [{}])[0].get("message", {})
            content = message.get("content", "")
            tool_calls = self.parse_tool_calls(llm_response)
            
            # 没有tool调用 → 结束循环，返回最终回复
            if not tool_calls:
                # fallback检查文本中的tool调用
                text_call = self.parse_text_tool_call(content)
                if text_call and loop_count < self.MAX_TOOL_LOOPS - 1:
                    # 执行文本格式的tool调用
                    result_msg = self.execute_tool_call(text_call)
                    messages.append({"role": "assistant", "content": content})
                    messages.append({"role": "user", "content": f"Tool执行结果:\n{result_msg['content']}\n\n请基于以上结果继续回答。"})
                    continue
                
                # 真正没有tool调用 → 最终回复
                if content:
                    messages.append({"role": "assistant", "content": content})
                return content, messages
            
            # 有tool调用 → 执行所有tool，结果回传给LLM
            messages.append(message)  # 把LLM的完整message加入历史
            
            for tool_call in tool_calls:
                print(f"[Tool调用] {tool_call['name']}({tool_call['arguments']})")
                result_msg = self.execute_tool_call(tool_call)
                print(f"[Tool结果] {result_msg['content'][:100]}")
                messages.append(result_msg)
        
        # 超过最大轮数 → 强制结束
        return "Tool调用轮数超过限制，请简化你的请求。", messages
    
    def __repr__(self):
        return f"ToolExecutor(max_loops={self.MAX_TOOL_LOOPS}, tools={len(self.registry._tools)})"