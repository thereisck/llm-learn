"""
LLM客户端 — 调用OpenAI API实现真实对话
支持多种provider（OpenAI/兼容中转站），兼容Chat Completions API
支持function calling（Tool调用）

设计原则：
- 优先环境变量配置，构造参数作为覆盖 — 方便零配置启动，也支持运行时定制
- 无API_KEY时自动降级为echo模式 — 保证无密钥环境也能跑通流程，不阻塞开发
- 使用urllib而非第三方库 — 最小依赖原则，避免引入openai/heavy SDK
- chat/chat_raw分层 — chat只关心文本，chat_raw保留完整响应供Tool循环解析
"""
import os
import json
from typing import Optional


class LLMClient:
    """
    LLM客户端 — 调用OpenAI Chat Completions API
    
    支持环境变量配置：
    - LLM_API_KEY: API密钥
    - LLM_BASE_URL: API基础URL（中转站）
    - LLM_MODEL: 模型名称
    
    也支持构造时传入参数
    
    设计原则：环境变量优先，构造参数覆盖 — 允许零配置启动，也支持运行时传入不同provider
    """
    
    # 设计原则：环境变量优先，构造参数覆盖 — 允许零配置启动，也支持运行时传入不同provider
    # _use_echo 标记是否降级为echo模式，避免每次chat都检查api_key
    def __init__(
        self,
        api_key: Optional[str] = None,
        base_url: Optional[str] = None,
        model: Optional[str] = None,
    ):
        self.api_key = api_key or os.environ.get("LLM_API_KEY", "")
        self.base_url = base_url or os.environ.get("LLM_BASE_URL", "https://api.openai.com/v1")
        self.model = model or os.environ.get("LLM_MODEL", "gpt-4o-mini")
        
        if not self.api_key:
            print("[LLM] 未配置API_KEY，将使用echo模式")
            self._use_echo = True
        else:
            self._use_echo = False
            print(f"[LLM] 已配置: model={self.model}, base_url={self.base_url}")
    
    def chat(self, messages: list[dict], max_tokens: int = 500, tools: list[dict] = None) -> str:
        """
        调用LLM生成回复（纯文本模式）
        
        messages格式：[{"role": "system/user/assistant", "content": "..."}]
        
        如果未配置API_KEY → fallback到echo模式
        
        设计原则：只返回文本内容，屏蔽API细节 — 适用于纯对话场景，
        不需要关心tool_calls等结构化信息
        """
        response = self.chat_raw(messages, max_tokens=max_tokens, tools=tools)
        
        # 从API响应中提取文本 — 统一走choices[0].message.content路径
        if isinstance(response, dict):
            choices = response.get("choices", [])
            if choices:
                content = choices[0].get("message", {}).get("content", "")
                return content or ""
        
        # fallback — chat_raw返回字符串时的兜底处理
        if isinstance(response, str):
            return response
        
        return ""
    
    def chat_raw(self, messages: list[dict], max_tokens: int = 500, tools: list[dict] = None) -> dict:
        """
        调用LLM并返回完整API响应（含tool_calls）
        
        返回OpenAI格式的完整响应dict，用于Tool调用循环解析
        
        设计原则：保留原始响应结构 — 让上层ToolExecutor能解析tool_calls字段，
        实现多轮Tool调用循环；错误时也返回结构化dict而非抛异常，保证调用方总能拿到choices
        """
        if self._use_echo:
            # echo模式：不走网络，直接返回模拟响应 — 开发调试用
            return self._echo_fallback_raw(messages)
        
        # 重试机制 — 长文本生成容易超时，最多重试2次
        max_retries = 2
        for attempt in range(max_retries + 1):
            try:
                import urllib.request
                import urllib.error
                import time
                
                url = f"{self.base_url}/chat/completions"
                headers = {
                    "Content-Type": "application/json",
                    "Authorization": f"Bearer {self.api_key}",
                }
                
                body_dict = {
                    "model": self.model,
                    "messages": messages,
                    "max_tokens": max_tokens,
                    "temperature": 0.7,
                }
                
                if tools:
                    body_dict["tools"] = tools
                
                body = json.dumps(body_dict)
                
                # 超时根据max_tokens动态调整 — 长文本需要更长等待
                timeout = max(120, max_tokens * 0.1)
                req = urllib.request.Request(url, data=body.encode("utf-8"), headers=headers)
                with urllib.request.urlopen(req, timeout=timeout) as resp:
                    data = json.loads(resp.read().decode("utf-8"))
                    return data
            
            except urllib.error.HTTPError as e:
                error_body = e.read().decode("utf-8")
                print(f"[LLM] HTTP错误 {e.code}: {error_body[:200]}")
                return {"error": f"API返回 {e.code}", "choices": [{"message": {"content": f"[LLM错误] API返回 {e.code}"}}]}
            
            except Exception as e:
                if attempt < max_retries:
                    print(f"[LLM] 第{attempt+1}次调用失败: {e}, 等待3秒后重试...")
                    time.sleep(3)
                else:
                    print(f"[LLM] 调用失败(已重试{max_retries}次): {e}")
                    return {"error": str(e), "choices": [{"message": {"content": f"[LLM错误] {str(e)}"}}]}
    
    def _echo_fallback(self, messages: list[dict]) -> str:
        """Echo模式 — 返回纯文本，仅供内部降级调用"""
        raw = self._echo_fallback_raw(messages)
        choices = raw.get("choices", [])
        if choices:
            return choices[0].get("message", {}).get("content", "")
        return ""
    
    def _echo_fallback_raw(self, messages: list[dict]) -> dict:
        """
        Echo模式 — 返回完整API响应格式
        
        模拟OpenAI API响应结构，让ToolExecutor可以统一处理
        
        设计原则：echo响应也严格遵循OpenAI格式 — 上层代码无需区分真实/echo模式，
        统一走choices[0].message解析路径
        """
        # 逆序取最后一条user消息 — 模拟"用户说了什么"的核心信息
        user_msg = ""
        for m in reversed(messages):
            if m["role"] == "user":
                user_msg = m["content"]
                break
        
        # 拼装prompt摘要 — 让开发者看到实际传入的prompt结构，辅助调试
        parts_info = []
        for m in messages:
            role = m["role"]
            content_len = len(m.get("content", "") or "")
            parts_info.append(f"  {role}: {content_len}字符")
        
        summary = f"[Echo模式] 收到: {user_msg}\n"
        summary += f"Prompt组装: {len(messages)}段\n"
        summary += "\n".join(parts_info)
        summary += "\n\n提示: 设置环境变量 LLM_API_KEY 后可接入真实LLM"
        
        return {
            "choices": [{
                "message": {
                    "role": "assistant",
                    "content": summary,
                },
                "finish_reason": "stop"
            }],
            "model": "echo-fallback"
        }
    
    def __repr__(self):
        # 简洁表示 — 快速识别当前客户端模式（echo/api）和模型，方便日志排查
        mode = "echo" if self._use_echo else "api"
        return f"LLMClient(mode={mode}, model={self.model})"