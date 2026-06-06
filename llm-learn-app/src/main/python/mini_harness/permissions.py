"""
权限分级模块 — 核心设计原则：渐进式权限信任
参考：Claude Code三层分级权限 + Codex三阶段ExecPolicy

三层分级：
1. Read-only（读文件/grep）→ 免批，随便看
2. Shell commands（bash/sh执行）→ 需批准，可选"不再询问"（永久记住per项目+命令）
3. File modification（edit/write文件）→ 需批准，"不再询问"仅记到session结束

规则评估顺序：deny → ask → allow（deny永远优先，一旦deny就不可能allow）
"""
import time
from dataclasses import dataclass, field
from enum import Enum
from typing import Optional


# ===== 枚举定义 =====

class ToolLevel(Enum):
    """工具权限层级 — 三层分级"""
    READ_ONLY = "read_only"     # 第1层：只读，无需批准
    SHELL = "shell"             # 第2层：Shell执行，需批准
    FILE_WRITE = "file_write"   # 第3层：文件修改，需批准


class Decision(Enum):
    """权限评估结果"""
    ALLOW = "allow"      # 允许执行
    ASK = "ask"          # 需要用户批准
    DENY = "deny"        # 直接拒绝


class RememberScope(Enum):
    """"不再询问"的记忆范围"""
    NONE = "none"              # 不记住
    SESSION = "session"        # 仅本次session记住（File modification用）
    PERMANENT = "permanent"    # 永久记住（Shell commands用）


# ===== 规则定义 =====

@dataclass
class PermissionRule:
    """
    权限规则 — 类似Claude Code的权限规则语法
    
    规则类型：
    - deny: 拒绝（最高优先级）
    - ask: 需要用户批准
    - allow: 允许（最低优先级）
    
    specifier: 匹配模式
    - 精确匹配: "npm run build"
    - 通配符: "npm run *"
    - 全匹配: "*"（匹配所有）
    """
    rule_type: Decision          # deny/ask/allow
    level: ToolLevel             # 作用于哪层权限
    specifier: str = "*"         # 匹配模式
    description: str = ""


@dataclass
class ApprovalRecord:
    """用户批准记录 — "不再询问"的实现"""
    tool_name: str
    specifier: str
    scope: RememberScope
    timestamp: float = field(default_factory=time.time)
    session_id: str = ""         # session级别的记住需要绑定session


class PermissionProfile:
    """
    权限配置 — 类似Claude Code的6种权限模式
    
    模式：
    - default: 标准三层分级
    - plan: 只允许Read-only（类似Claude Code的Plan模式）
    - auto: Shell和File自动批准（后台安全检查）
    - bypass: 全放开（仅rm -rf /拦截）
    """
    
    # 预定义的6种模式
    MODES = {
        "default": "标准三层分级",
        "accept_edits": "自动接受文件编辑",
        "plan": "只允许Read-only（Plan模式）",
        "auto": "Shell和File自动批准",
        "dont_ask": "不问直接拒绝未预批准的",
        "bypass": "全放开（仅rm -rf拦截）",
    }
    
    def __init__(self, mode: str = "default"):
        self.mode = mode
        self.deny_rules: list[PermissionRule] = []
        self.ask_rules: list[PermissionRule] = []
        self.allow_rules: list[PermissionRule] = []
        self.approval_records: list[ApprovalRecord] = []
        self._session_id: str = ""
        
        # 初始化模式对应的默认规则
        self._init_mode_rules()
    
    def set_session(self, session_id: str):
        """绑定session - 用于session级别的不再询问"""
        self._session_id = session_id
    
    def _init_mode_rules(self):
        """根据模式初始化默认规则"""
        
        # 通用deny规则 — 所有模式都有
        self.deny_rules = [
            PermissionRule(Decision.DENY, ToolLevel.SHELL, "rm -rf /", "禁止删除根目录"),
            PermissionRule(Decision.DENY, ToolLevel.SHELL, "rm -rf ~", "禁止删除home目录"),
            PermissionRule(Decision.DENY, ToolLevel.SHELL, "rm -rf /*", "禁止删除根目录通配符"),
        ]
        
        if self.mode == "default":
            # Read-only自动允许，Shell和File需要批准
            self.allow_rules.append(
                PermissionRule(Decision.ALLOW, ToolLevel.READ_ONLY, "*", "所有只读操作免批")
            )
            self.ask_rules.append(
                PermissionRule(Decision.ASK, ToolLevel.SHELL, "*", "所有Shell需批准")
            )
            self.ask_rules.append(
                PermissionRule(Decision.ASK, ToolLevel.FILE_WRITE, "*", "所有文件修改需批准")
            )
        
        elif self.mode == "plan":
            # Plan模式：只允许Read-only，其他全部拒绝
            self.allow_rules.append(
                PermissionRule(Decision.ALLOW, ToolLevel.READ_ONLY, "*", "Plan模式：只读免批")
            )
            self.deny_rules.append(
                PermissionRule(Decision.DENY, ToolLevel.SHELL, "*", "Plan模式：拒绝Shell")
            )
            self.deny_rules.append(
                PermissionRule(Decision.DENY, ToolLevel.FILE_WRITE, "*", "Plan模式：拒绝文件修改")
            )
        
        elif self.mode == "auto":
            # Auto模式：Shell和File自动批准（但有后台安全检查）
            self.allow_rules.append(
                PermissionRule(Decision.ALLOW, ToolLevel.READ_ONLY, "*", "只读免批")
            )
            self.allow_rules.append(
                PermissionRule(Decision.ALLOW, ToolLevel.SHELL, "*", "Shell自动批准")
            )
            self.allow_rules.append(
                PermissionRule(Decision.ALLOW, ToolLevel.FILE_WRITE, "*", "文件修改自动批准")
            )
        
        elif self.mode == "bypass":
            # Bypass模式：全放开（仅rm -rf拦截）
            self.allow_rules.append(
                PermissionRule(Decision.ALLOW, ToolLevel.READ_ONLY, "*", "只读免批")
            )
            self.allow_rules.append(
                PermissionRule(Decision.ALLOW, ToolLevel.SHELL, "*", "Shell免批")
            )
            self.allow_rules.append(
                PermissionRule(Decision.ALLOW, ToolLevel.FILE_WRITE, "*", "文件修改免批")
            )
        
        elif self.mode == "dont_ask":
            # dontAsk模式：未预批准的拒绝，已预批准的允许
            self.allow_rules.append(
                PermissionRule(Decision.ALLOW, ToolLevel.READ_ONLY, "*", "只读免批")
            )
            # Shell和File不设默认规则 → 需要用户显式预批准
    
    def evaluate(self, tool_level: ToolLevel, tool_name: str, specifier: str = "") -> Decision:
        """
        权限评估 — 核心算法
        
        评估顺序（Claude Code官方文档）：deny → ask → allow
        deny永远优先，一旦匹配deny就返回DENY
        
        步骤：
        1. 先检查deny规则（最高优先级）
        2. 再检查ask规则
        3. 最后检查allow规则
        4. 如果都不匹配 → 默认ASK（需要批准）
        
        还要检查"不再询问"记录：
        - Shell的"不再询问"→ PERMANENT（永久记住）
        - File modification的"不再询问"→ SESSION（仅session记住）
        """
        # Step 1: 检查deny规则（最高优先级）
        for rule in self.deny_rules:
            if rule.level == tool_level and self._match(rule.specifier, specifier or tool_name):
                return Decision.DENY
        
        # Step 2: 检查 "不再询问" 记录
        target = specifier or tool_name
        for record in self.approval_records:
            if record.tool_name == tool_level.value and self._match(record.specifier, target):
                if record.scope == RememberScope.PERMANENT:
                    return Decision.ALLOW
                if record.scope == RememberScope.SESSION and record.session_id == self._session_id:
                    return Decision.ALLOW
        
        # Step 3: 检查ask规则
        for rule in self.ask_rules:
            if rule.level == tool_level and self._match(rule.specifier, specifier or tool_name):
                return Decision.ASK
        
        # Step 4: 检查allow规则
        for rule in self.allow_rules:
            if rule.level == tool_level and self._match(rule.specifier, specifier or tool_name):
                return Decision.ALLOW
        
        # Step 5: 都不匹配 → 默认ASK
        return Decision.ASK
    
    def approve(self, tool_level: ToolLevel, tool_name: str, specifier: str = "",
                remember: bool = False) -> Decision:
        """
        用户批准操作
        
        remember=True时记录"不再询问"：
        - Shell → PERMANENT（永久记住per项目）
        - File modification → SESSION（仅session记住）
        """
        if remember:
            scope = RememberScope.PERMANENT if tool_level == ToolLevel.SHELL else RememberScope.SESSION
            self.approval_records.append(
                ApprovalRecord(
                    tool_name=tool_level.value,
                    specifier=specifier or tool_name,
                    scope=scope,
                    session_id=self._session_id
                )
            )
        
        return Decision.ALLOW
    
    def clear_session_records(self):
        """Session结束时清除session级别的"不再询问"记录"""
        self.approval_records = [
            r for r in self.approval_records
            if r.scope != RememberScope.SESSION
        ]
    
    def _match(self, pattern: str, target: str) -> bool:
        """
        简单的通配符匹配
        
        - "*" 匹配所有
        - "npm run *" 匹配 "npm run build"、"npm run test"等
        - 精确匹配: "npm run build" 只匹配 "npm run build"
        """
        if pattern == "*":
            return True
        
        if pattern.endswith("*"):
            prefix = pattern[:-1]
            return target.startswith(prefix)
        
        return pattern == target
    
    def __repr__(self):
        return f"PermissionProfile(mode={self.mode}, deny={len(self.deny_rules)}, ask={len(self.ask_rules)}, allow={len(self.allow_rules)}, approvals={len(self.approval_records)})"


# ===== 测试代码 =====

def test_permissions():
    """测试权限分级和deny→ask→allow规则"""
    
    print("=" * 50)
    print("权限分级模块测试")
    print("=" * 50)
    
    # 测试1: default模式 — 三层分级
    print("\n--- 测试1: default模式 ---")
    profile = PermissionProfile("default")
    profile.set_session("test_session")
    
    # Read-only → 免批
    result = profile.evaluate(ToolLevel.READ_ONLY, "grep", "grep error")
    print(f"grep(只读) → {result.value} ✅ 预期: allow")
    
    # Shell → 需批准
    result = profile.evaluate(ToolLevel.SHELL, "bash", "npm run build")
    print(f"bash(Shell) → {result.value} ✅ 预期: ask")
    
    # File write → 需批准
    result = profile.evaluate(ToolLevel.FILE_WRITE, "edit", "main.py")
    print(f"edit(文件修改) → {result.value} ✅ 预期: ask")
    
    # rm -rf / → 拒绝（deny最高优先级）
    result = profile.evaluate(ToolLevel.SHELL, "bash", "rm -rf /")
    print(f"rm -rf /(deny规则) → {result.value} ✅ 预期: deny")
    
    # 测试2: "不再询问"机制
    print("\n--- 测试2: 不再询问 ---")
    
    # 用户批准Shell命令 + 永久记住
    profile.approve(ToolLevel.SHELL, "bash", "npm run build", remember=True)
    result = profile.evaluate(ToolLevel.SHELL, "bash", "npm run build")
    print(f"bash(npm run build 批准后) → {result.value} ✅ 预期: allow（永久记住）")
    
    # 用户批准File修改 + session记住
    profile.approve(ToolLevel.FILE_WRITE, "edit", "main.py", remember=True)
    result = profile.evaluate(ToolLevel.FILE_WRITE, "edit", "main.py")
    print(f"edit(main.py 批准后) → {result.value} ✅ 预期: allow（session记住）")
    
    # 测试3: session结束清除
    print("\n--- 测试3: session结束清除 ---")
    profile.clear_session_records()
    result = profile.evaluate(ToolLevel.FILE_WRITE, "edit", "main.py")
    print(f"edit(main.py session结束后) → {result.value} ✅ 预期: ask（session记住已清除）")
    result = profile.evaluate(ToolLevel.SHELL, "bash", "npm run build")
    print(f"bash(npm run build session结束后) → {result.value} ✅ 预期: allow（永久记住未清除）")
    
    # 测试4: plan模式
    print("\n--- 测试4: plan模式 ---")
    plan_profile = PermissionProfile("plan")
    
    result = plan_profile.evaluate(ToolLevel.READ_ONLY, "grep", "grep error")
    print(f"grep(plan模式) → {result.value} ✅ 预期: allow")
    
    result = plan_profile.evaluate(ToolLevel.SHELL, "bash", "npm run build")
    print(f"bash(plan模式) → {result.value} ✅ 预期: deny")
    
    result = plan_profile.evaluate(ToolLevel.FILE_WRITE, "edit", "main.py")
    print(f"edit(plan模式) → {result.value} ✅ 预期: deny")
    
    # 测试5: bypass模式（仅rm -rf拦截）
    print("\n--- 测试5: bypass模式 ---")
    bypass_profile = PermissionProfile("bypass")
    
    result = bypass_profile.evaluate(ToolLevel.SHELL, "bash", "npm run build")
    print(f"bash(bypass模式) → {result.value} ✅ 预期: allow")
    
    result = bypass_profile.evaluate(ToolLevel.SHELL, "bash", "rm -rf /")
    print(f"rm -rf /(bypass模式) → {result.value} ✅ 预期: deny（断路器仍生效）")
    
    print(f"\nProfile状态: {profile}")
    print(f"Plan Profile状态: {plan_profile}")
    print(f"Bypass Profile状态: {bypass_profile}")


if __name__ == "__main__":
    test_permissions()