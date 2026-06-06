"""
Tool执行层 — 让Agent真正能干活

参考四大Agent的Tool设计：
- OpenClaw: exec/read/write/edit/web_fetch等内置工具 + MCP外部工具
- Claude Code: Read/Edit/Bash/Glob/Grep/LS等工具 + 权限分级
- Codex: apply_patch + shell + file_search + web_search
- Hermes: 70+注册工具 Central Registry

设计原则：
1. 每个Tool自描述（name/description/parameters），LLM能看懂怎么调用
2. Tool执行前走权限检查（permissions.py的三层分级）
3. Tool执行结果返回给LLM，形成 observation→reasoning 循环
4. Tool调用格式用JSON（和OpenAI function calling一致）
"""
import os
import subprocess
import json
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Optional, Any


@dataclass
class ToolResult:
    """Tool执行结果"""
    success: bool
    output: str = ""       # 标准输出/主要内容
    error: str = ""        # 错误信息
    metadata: dict = field(default_factory=dict)  # 额外信息
    
    def __str__(self):
        if self.success:
            return self.output[:500]
        return f"[错误] {self.error}"
    
    def to_llm_message(self) -> str:
        """格式化为LLM可读的消息"""
        if self.success:
            result = self.output
            if len(result) > 2000:
                result = result[:2000] + f"\n... (截断，完整输出有{len(self.output)}字符)"
            return result
        return f"Tool执行失败: {self.error}"


class BaseTool(ABC):
    """
    Tool基类 — 所有Tool必须继承
    
    每个Tool需要定义：
    - name: 工具名称（LLM调用时用这个名字）
    - description: 工具描述（LLM根据描述判断什么时候该用）
    - parameters: 参数schema（JSON Schema格式）
    - tool_level: 需要什么权限级别才能执行
    """
    
    name: str = ""
    description: str = ""
    parameters: dict = {}
    tool_level: str = "read_only"  # read_only / shell / file_write
    
    @abstractmethod
    def execute(self, **kwargs) -> ToolResult:
        """执行工具，返回ToolResult"""
        pass
    
    def get_schema(self) -> dict:
        """返回OpenAI function calling格式的schema"""
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": self.parameters,
            }
        }
    
    def validate_params(self, kwargs: dict) -> list[str]:
        """验证参数，返回错误列表"""
        errors = []
        required = self.parameters.get("required", [])
        props = self.parameters.get("properties", {})
        
        for req in required:
            if req not in kwargs:
                errors.append(f"缺少必填参数: {req}")
        
        for key in kwargs:
            if key not in props:
                errors.append(f"未知参数: {key}")
        
        return errors
    
    def __repr__(self):
        return f"Tool({self.name}, level={self.tool_level})"


# ============================================================
# 4个内置Tool
# ============================================================

class ReadFileTool(BaseTool):
    """读取文件内容"""
    name = "read_file"
    description = "读取指定文件的内容。支持offset和limit参数读取部分内容（大文件分段读）。"
    parameters = {
        "type": "object",
        "properties": {
            "path": {
                "type": "string",
                "description": "文件路径（相对或绝对路径）"
            },
            "offset": {
                "type": "integer",
                "description": "起始行号（1-based），默认1"
            },
            "limit": {
                "type": "integer",
                "description": "读取行数，默认50"
            }
        },
        "required": ["path"]
    }
    tool_level = "read_only"
    
    def execute(self, **kwargs) -> ToolResult:
        errors = self.validate_params(kwargs)
        if errors:
            return ToolResult(success=False, error="; ".join(errors))
        
        path = kwargs["path"]
        offset = kwargs.get("offset", 1)
        limit = kwargs.get("limit", 50)
        
        # 安全检查：防止路径穿越
        if ".." in path:
            return ToolResult(success=False, error="路径不允许包含..")
        
        if not os.path.exists(path):
            return ToolResult(success=False, error=f"文件不存在: {path}")
        
        try:
            with open(path, "r", encoding="utf-8") as f:
                lines = f.readlines()
            selected = lines[offset-1 : offset-1+limit]
            content = "".join(selected)
            total_lines = len(lines)
            shown_lines = len(selected)
            
            header = f"文件: {path} (共{total_lines}行, 显示第{offset}-{offset+shown_lines-1}行)\n"
            header += "=" * 40 + "\n"
            
            return ToolResult(
                success=True,
                output=header + content,
                metadata={"total_lines": total_lines, "shown_lines": shown_lines}
            )
        except Exception as e:
            return ToolResult(success=False, error=str(e))


class WriteFileTool(BaseTool):
    """写入文件内容（覆盖或追加）"""
    name = "write_file"
    description = "写入内容到文件。mode=overwrite覆盖写入，mode=append追加到末尾。会自动创建不存在的文件和父目录。"
    parameters = {
        "type": "object",
        "properties": {
            "path": {
                "type": "string",
                "description": "文件路径"
            },
            "content": {
                "type": "string",
                "description": "要写入的内容"
            },
            "mode": {
                "type": "string",
                "description": "写入模式: overwrite(覆盖) 或 append(追加)，默认overwrite"
            }
        },
        "required": ["path", "content"]
    }
    tool_level = "file_write"
    
    def execute(self, **kwargs) -> ToolResult:
        errors = self.validate_params(kwargs)
        if errors:
            return ToolResult(success=False, error="; ".join(errors))
        
        path = kwargs["path"]
        content = kwargs["content"]
        mode = kwargs.get("mode", "overwrite")
        
        if ".." in path:
            return ToolResult(success=False, error="路径不允许包含..")
        
        try:
            parent = os.path.dirname(path)
            if parent:
                os.makedirs(parent, exist_ok=True)
            
            if mode == "append":
                with open(path, "a", encoding="utf-8") as f:
                    f.write(content)
                action = "追加"
            else:
                with open(path, "w", encoding="utf-8") as f:
                    f.write(content)
                action = "写入"
            
            file_size = os.path.getsize(path)
            return ToolResult(
                success=True,
                output=f"已{action}文件: {path} ({file_size}字节)",
                metadata={"path": path, "mode": mode, "size": file_size}
            )
        except Exception as e:
            return ToolResult(success=False, error=str(e))


class ExecShellTool(BaseTool):
    """执行Shell命令"""
    name = "exec_shell"
    description = "执行Shell命令并返回输出。命令在当前工作目录下执行，有超时限制（默认10秒）。危险命令会被拦截。"
    parameters = {
        "type": "object",
        "properties": {
            "command": {
                "type": "string",
                "description": "要执行的Shell命令"
            },
            "timeout": {
                "type": "integer",
                "description": "超时秒数，默认10"
            }
        },
        "required": ["command"]
    }
    tool_level = "shell"
    
    # 危险命令拦截（参考Codex BANNED_PREFIX）
    BANNED_COMMANDS = ["rm -rf /", "sudo rm", "mkfs", "dd if="]
    
    def execute(self, **kwargs) -> ToolResult:
        errors = self.validate_params(kwargs)
        if errors:
            return ToolResult(success=False, error="; ".join(errors))
        
        command = kwargs["command"]
        timeout = kwargs.get("timeout", 10)
        
        # 危险命令拦截
        for banned in self.BANNED_COMMANDS:
            if command.strip().startswith(banned):
                return ToolResult(success=False, error=f"危险命令被拦截: {banned}")
        
        try:
            proc = subprocess.run(
                command,
                shell=True,
                capture_output=True,
                text=True,
                timeout=timeout,
            )
            
            output = proc.stdout
            if proc.stderr:
                output += f"\n[stderr] {proc.stderr}"
            
            if len(output) > 3000:
                output = output[:3000] + f"\n... (截断，完整输出有{len(proc.stdout)}字符)"
            
            return ToolResult(
                success=proc.returncode == 0,
                output=output,
                error=proc.stderr if proc.returncode != 0 else "",
                metadata={"returncode": proc.returncode}
            )
        except subprocess.TimeoutExpired:
            return ToolResult(success=False, error=f"命令超时({timeout}秒)")
        except Exception as e:
            return ToolResult(success=False, error=str(e))


class SearchCodeTool(BaseTool):
    """搜索代码文件（grep + 文件列表）"""
    name = "search_code"
    description = "搜索代码内容或文件名。mode=content搜索文件内容(grep)，mode=filename搜索文件名(find)。"
    parameters = {
        "type": "object",
        "properties": {
            "query": {
                "type": "string",
                "description": "搜索关键词"
            },
            "path": {
                "type": "string",
                "description": "搜索目录，默认当前目录"
            },
            "mode": {
                "type": "string",
                "description": "搜索模式: content(内容) 或 filename(文件名)，默认content"
            }
        },
        "required": ["query"]
    }
    tool_level = "read_only"
    
    def execute(self, **kwargs) -> ToolResult:
        errors = self.validate_params(kwargs)
        if errors:
            return ToolResult(success=False, error="; ".join(errors))
        
        query = kwargs["query"]
        path = kwargs.get("path", ".")
        mode = kwargs.get("mode", "content")
        
        if ".." in path:
            return ToolResult(success=False, error="路径不允许包含..")
        
        try:
            if mode == "filename":
                cmd = f"find {path} -name '*{query}*' -type f | head -20"
            else:
                cmd = f"grep -rn '{query}' {path} --include='*.py' --include='*.js' --include='*.java' --include='*.ts' --include='*.md' | head -20"
            
            proc = subprocess.run(
                cmd, shell=True, capture_output=True, text=True, timeout=10,
            )
            
            output = proc.stdout if proc.stdout else "没有找到匹配结果"
            
            return ToolResult(
                success=True,
                output=output,
                metadata={"mode": mode, "query": query}
            )
        except Exception as e:
            return ToolResult(success=False, error=str(e))


# ============================================================
# ToolRegistry — 注册+查找+权限检查
# ============================================================

class ToolRegistry:
    """
    Tool注册中心 — 管理所有可用Tool
    
    功能：
    1. 注册Tool（内置+外部）
    2. 按名称查找Tool
    3. 生成tool schema列表（给LLM看的function calling格式）
    """
    
    def __init__(self):
        self._tools: dict[str, BaseTool] = {}
        
        # 注册4个内置Tool
        self.register(ReadFileTool())
        self.register(WriteFileTool())
        self.register(ExecShellTool())
        self.register(SearchCodeTool())
    
    def register(self, tool: BaseTool):
        """注册一个Tool"""
        self._tools[tool.name] = tool
        print(f"[Tools] 注册: {tool.name} (权限={tool.tool_level})")
    
    def get(self, name: str) -> Optional[BaseTool]:
        """按名称查找Tool"""
        return self._tools.get(name)
    
    def list_all(self) -> list[BaseTool]:
        """列出所有Tool"""
        return list(self._tools.values())
    
    def get_schemas(self) -> list[dict]:
        """生成所有Tool的schema（给LLM看）"""
        return [tool.get_schema() for tool in self._tools.values()]
    
    def __repr__(self):
        tools = ", ".join(f"{t.name}({t.tool_level})" for t in self._tools.values())
        return f"ToolRegistry({tools})"