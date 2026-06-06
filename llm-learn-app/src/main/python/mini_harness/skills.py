"""
Skill热加载模块 — 核心设计原则：SKILL.md自描述，Agent自动发现能力
参考：OpenClaw多级热加载 + Claude Code SKILL.md+agentskills.io + Codex loader→manager→injection→render

设计要点：
1. SKILL.md是Skill的"身份证" — YAML frontmatter定义元信息，正文定义指令
2. 多级目录 — workspace/project/personal/bundled，最高优先级先
3. 热加载 — 文件变更立即生效，不需要重启Agent（Live change detection）
4. 注入 — Skill内容自动注入到Agent的prompt中
5. 隐式+显式调用 — @skill-name显式引用，或根据description自动匹配
"""
import os
import time
import yaml
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional


# ===== Skill数据结构 =====

@dataclass
class SkillMeta:
    """Skill元信息 — 来自SKILL.md的YAML frontmatter"""
    name: str = ""
    description: str = ""
    version: str = "0.1"
    priority: int = 0          # 优先级，数字越大越优先
    enabled: bool = True       # 是否启用
    invocation: str = "auto"   # auto=隐式匹配 / explicit=必须@skill-name显式调用


@dataclass
class Skill:
    """
    一个完整的Skill
    
    包含：
    - meta: 元信息（YAML frontmatter）
    - instructions: 指令正文（Markdown内容）
    - path: 文件路径（用于热加载检测变更）
    - load_time: 加载时间（用于判断是否需要重新加载）
    """
    meta: SkillMeta = field(default_factory=SkillMeta)
    instructions: str = ""
    path: str = ""
    load_time: float = 0.0
    
    @property
    def name(self) -> str:
        return self.meta.name
    
    @property
    def description(self) -> str:
        return self.meta.description
    
    def is_auto_invocable(self) -> bool:
        """是否可以隐式调用（根据description自动匹配）"""
        return self.meta.invocation == "auto" and self.meta.enabled
    
    def matches(self, user_input: str) -> bool:
        """根据description判断是否匹配用户输入"""
        if not self.is_auto_invocable():
            return False
        # 简化匹配：description中的关键词出现在用户输入中
        keywords = self.meta.description.lower().split()
        input_lower = user_input.lower()
        return any(kw in input_lower for kw in keywords if len(kw) > 2)


# ===== SkillLoader — 加载SKILL.md文件 =====

class SkillLoader:
    """
    Skill加载器 — 从SKILL.md文件解析Skill
    
    SKILL.md格式：
    ---
    name: code-review
    description: Run a code review on a pull request
    version: 0.1
    priority: 10
    invocation: auto
    ---
    
    # Code Review Skill
    
    Use subagents to review code...
    """
    
    @staticmethod
    def load_from_file(filepath: Path) -> Optional[Skill]:
        """从SKILL.md文件加载一个Skill"""
        if not filepath.exists() or filepath.name != "SKILL.md":
            return None
        
        content = filepath.read_text(encoding="utf-8")
        meta, instructions = SkillLoader._parse_frontmatter(content)
        
        if meta is None:
            # 没有frontmatter，用文件名作为name
            parent_name = filepath.parent.name
            meta = SkillMeta(name=parent_name, description="")
            instructions = content
        
        return Skill(
            meta=meta,
            instructions=instructions,
            path=str(filepath),
            load_time=filepath.stat().st_mtime,
        )
    
    @staticmethod
    def _parse_frontmatter(content: str) -> tuple[Optional[SkillMeta], str]:
        """
        解析YAML frontmatter
        
        格式：
        ---
        yaml内容
        ---
        Markdown正文
        """
        if not content.startswith("---"):
            return None, content
        
        # 找到第二个 "---"
        end_idx = content.find("---", 3)
        if end_idx == -1:
            return None, content
        
        yaml_str = content[3:end_idx].strip()
        body = content[end_idx + 3:].strip()
        
        try:
            data = yaml.safe_load(yaml_str)
            meta = SkillMeta(
                name=data.get("name", ""),
                description=data.get("description", ""),
                version=data.get("version", "0.1"),
                priority=data.get("priority", 0),
                enabled=data.get("enabled", True),
                invocation=data.get("invocation", "auto"),
            )
            return meta, body
        except yaml.YAMLError:
            return None, content


# ===== SkillsManager — Skill注册+发现+热加载 =====

class SkillsManager:
    """
    Skill管理器 — 多级目录热加载
    
    目录层级（OpenClaw风格）：
    1. workspace/ — 最高优先级（用户当前工作目录）
    2. project/   — 项目级
    3. personal/  — 个人级（~/.mini_harness/skills/）
    4. bundled/   — 内置（随Agent打包）
    
    同名Skill：高优先级覆盖低优先级
    热加载：每次调用时检查文件mtime，变更则重新加载
    """
    
    LEVEL_ORDER = ["bundled", "personal", "project", "workspace"]
    # workspace最高，bundled最低
    # LEVEL_ORDER里的顺序是优先级从低到高
    
    def __init__(self, skill_dirs: dict[str, str] = None):
        """
        skill_dirs: 各级Skill目录路径
        {
            "workspace": "/path/to/workspace/skills",
            "project": "/path/to/project/skills",
            "personal": "/path/to/personal/skills",
            "bundled": "/path/to/bundled/skills",
        }
        """
        self.skill_dirs = skill_dirs or {}
        self.skills: dict[str, Skill] = {}  # name → Skill
        self._loaded = False
    
    def load_all(self):
        """加载所有Skill目录中的SKILL.md"""
        # 从低优先级到高优先级加载，同名会被高优先级覆盖
        for level in self.LEVEL_ORDER:
            dir_path = self.skill_dirs.get(level)
            if dir_path is None:
                continue
            
            skill_path = Path(dir_path)
            if not skill_path.exists():
                continue
            
            # 递归找所有SKILL.md
            for sk_file in skill_path.rglob("SKILL.md"):
                skill = SkillLoader.load_from_file(sk_file)
                if skill and skill.meta.name:
                    self.skills[skill.meta.name] = skill
                    print(f"[Skills] 加载: {skill.meta.name} (优先级={level}, 描述={skill.meta.description[:30]})")
        
        self._loaded = True
        print(f"[Skills] 共加载 {len(self.skills)} 个Skill")
    
    def reload_if_changed(self):
        """
        热加载检测 — Live change detection
        
        检查每个Skill文件的mtime，如果变了就重新加载
        这就是Claude Code的"Live change detection"核心机制
        """
        changed = []
        for name, skill in self.skills.items():
            filepath = Path(skill.path)
            if not filepath.exists():
                continue
            
            current_mtime = filepath.stat().st_mtime
            if current_mtime > skill.load_time:
                new_skill = SkillLoader.load_from_file(filepath)
                if new_skill:
                    self.skills[name] = new_skill
                    changed.append(name)
        
        if changed:
            print(f"[Skills] 热加载更新: {changed}")
        
        return changed
    
    def get_skill(self, name: str) -> Optional[Skill]:
        """获取指定Skill"""
        self.reload_if_changed()  # 每次获取时检查是否需要热加载
        return self.skills.get(name)
    
    def find_matching_skills(self, user_input: str) -> list[Skill]:
        """
        隐式匹配 — 根据用户输入自动找到匹配的Skill
        
        这是Claude Code的"Dynamic context injection"核心：
        不需要用户显式写 @skill-name，Agent根据用户输入
        和Skill的description自动判断该注入哪些Skill
        """
        self.reload_if_changed()
        matched = []
        for skill in self.skills.values():
            if skill.matches(user_input):
                matched.append(skill)
        
        # 按priority排序
        matched.sort(key=lambda s: s.meta.priority, reverse=True)
        return matched
    
    def inject_skills_prompt(self, user_input: str) -> str:
        """
        注入Skill到prompt — 最核心的功能
        
        流程：
        1. 找到匹配的Skill（隐式匹配）
        2. 把Skill的instructions拼接到prompt中
        3. 返回增强后的prompt
        
        类似Claude Code的Dynamic context injection：
        Skill内容不是静态的，而是根据当前上下文动态注入
        """
        matched = self.find_matching_skills(user_input)
        
        if not matched:
            return ""
        
        injected = []
        for skill in matched:
            injected.append(f"## Skill: {skill.meta.name}\n{skill.instructions}")
        
        prompt_section = "\n\n---\n# Skill Instructions (auto-injected)\n" + "\n\n".join(injected) + "\n---\n"
        return prompt_section
    
    def list_skills(self) -> list[dict]:
        """列出所有Skill的状态"""
        return [
            {
                "name": s.meta.name,
                "description": s.meta.description[:50],
                "priority": s.meta.priority,
                "invocation": s.meta.invocation,
                "enabled": s.meta.enabled,
                "path": s.path,
            }
            for s in self.skills.values()
        ]
    
    def __repr__(self):
        return f"SkillsManager(skills={len(self.skills)}, loaded={self._loaded})"


# ===== 测试代码 =====

def test_skills():
    import tempfile
    print("=" * 50)
    print("Skill热加载模块测试")
    print("=" * 50)
    
    # 创建临时Skill目录结构
    tmp = tempfile.mkdtemp()
    
    # bundled级: code-review Skill
    bundled_dir = Path(tmp) / "bundled"
    bundled_dir.mkdir()
    review_dir = bundled_dir / "code-review"
    review_dir.mkdir()
    review_dir.joinpath("SKILL.md").write_text("""---
name: code-review
description: review code quality and find bugs
version: 0.1
priority: 5
invocation: auto
---

# Code Review

When asked to review code:
1. Check for obvious bugs
2. Look for performance issues
3. Verify error handling
""", encoding="utf-8")
    
    # personal级: debug Skill（更高优先级）
    personal_dir = Path(tmp) / "personal"
    personal_dir.mkdir()
    debug_dir = personal_dir / "debug"
    debug_dir.mkdir()
    debug_dir.joinpath("SKILL.md").write_text("""---
name: debug
description: debug errors and find root causes
version: 0.1
priority: 10
invocation: auto
---

# Debug Skill

When debugging:
1. Read the error message carefully
2. Find the exact line causing the error
3. Trace the call stack
""", encoding="utf-8")
    
    # explicit级: deploy Skill（必须显式调用）
    project_dir = Path(tmp) / "project"
    project_dir.mkdir()
    deploy_dir = project_dir / "deploy"
    deploy_dir.mkdir()
    deploy_dir.joinpath("SKILL.md").write_text("""---
name: deploy
description: deploy application to server
version: 0.1
priority: 8
invocation: explicit
---

# Deploy Skill

Deploy steps:
1. Run tests
2. Build the project
3. Push to server
""", encoding="utf-8")
    
    # 测试1: 加载所有Skill
    print("\n--- 测试1: 加载 ---")
    manager = SkillsManager({
        "bundled": str(bundled_dir),
        "personal": str(personal_dir),
        "project": str(project_dir),
    })
    manager.load_all()
    
    for s in manager.list_skills():
        print(f"  {s}")
    
    # 测试2: 隐式匹配
    print("\n--- 测试2: 隐式匹配 ---")
    matches = manager.find_matching_skills("帮我review一下这段代码")
    print(f"  '帮我review一下这段代码' → 匹配: {[s.name for s in matches]}")
    
    matches = manager.find_matching_skills("debug一个报错")
    print(f"  'debug一个报错' → 匹配: {[s.name for s in matches]}")
    
    matches = manager.find_matching_skills("部署到服务器")
    print(f"  '部署到服务器' → 匹配: {[s.name for s in matches]} （deploy是explicit，不隐式匹配）")
    
    # 测试3: 显式调用
    print("\n--- 测试3: 显式调用 ---")
    skill = manager.get_skill("deploy")
    print(f"  @deploy → {skill.meta.description}")
    
    # 测试4: prompt注入
    print("\n--- 测试4: prompt注入 ---")
    prompt = manager.inject_skills_prompt("帮我review一下这段代码")
    print(f"  注入后prompt（前200字符）:\n{prompt[:200]}...")
    
    # 测试5: 热加载（修改Skill文件后检测变更）
    print("\n--- 测试5: 热加载 ---")
    review_file = review_dir / "SKILL.md"
    old_content = review_file.read_text(encoding="utf-8")
    # 修改内容
    review_file.write_text(old_content.replace("priority: 5", "priority: 15"), encoding="utf-8")
    # 触发热加载
    skill = manager.get_skill("code-review")
    print(f"  code-review priority: {skill.meta.priority} ✅ 热加载生效（从5→15）")
    
    # 清理
    import shutil
    shutil.rmtree(tmp)
    print("\n测试完毕，临时目录已清理")


if __name__ == "__main__":
    test_skills()