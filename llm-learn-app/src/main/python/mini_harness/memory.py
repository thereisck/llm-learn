"""
记忆存储模块 — 核心设计原则：Agent主动策展记忆，不是被动日志
参考：OpenClaw纯文件记忆 + Hermes双文件+nudge + Claude Code CLAUDE.md+Auto memory

三层记忆架构：
1. MEMORY.md — 长期记忆（Agent主动策展，容量管理）
2. 日志 YYYY-MM-DD.md — 每日原始记录
3. search — 关键词搜索（简化版向量检索）

容量管理（Hermes风格）：
- MEMORY.md 有容量上限（默认2000字符）
- 超过上限必须先合并旧的再写新的
- nudge驱动：Agent主动判断什么时候该写笔记
"""
import os
import json
from datetime import datetime, date
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional


# ===== 数据结构 =====

@dataclass
class MemoryEntry:
    """一条记忆条目"""
    category: str       # 分类：decision/lesson/fact/preference
    content: str        # 内容
    timestamp: str = field(default_factory=lambda: datetime.now().strftime("%H:%M"))
    project: str = ""   # 关联项目


@dataclass
class DailyLog:
    """每日日志条目"""
    project: str
    title: str
    conclusion: str         # 一句话总结
    files_changed: list     # 涉及的文件
    lesson: str = ""        # 踩坑点
    tags: list = field(default_factory=list)


class MemoryManager:
    """
    记忆管理器 — 三层架构
    
    目录结构：
    memory_dir/
    ├── MEMORY.md           # 长期记忆（策展）
    ├── 2026-06-06.md       # 今日日志
    ├── 2026-06-05.md       # 昨日日志
    └── ...
    """
    
    MEMORY_CAPACITY = 2000   # MEMORY.md容量上限（字符数）
    
    def __init__(self, memory_dir: str = "memory_dir"):
        self.memory_dir = Path(memory_dir)
        self.memory_dir.mkdir(parents=True, exist_ok=True)
        self.memory_file = self.memory_dir / "MEMORY.md"
        
        # 初始化MEMORY.md（如果不存在）
        if not self.memory_file.exists():
            self._write_memory_file("# Agent Long-Term Memory\n\n")
    
    # ===== 长期记忆（MEMORY.md）=====
    
    def read_memory(self) -> str:
        """读取MEMORY.md全部内容"""
        if self.memory_file.exists():
            return self.memory_file.read_text(encoding="utf-8")
        return ""
    
    def memory_capacity_info(self) -> dict:
        """查看MEMORY.md容量状态"""
        content = self.read_memory()
        current_len = len(content)
        return {
            "current_chars": current_len,
            "max_chars": self.MEMORY_CAPACITY,
            "usage_pct": round(current_len / self.MEMORY_CAPACITY * 100, 1),
            "is_full": current_len >= self.MEMORY_CAPACITY,
            "remaining_chars": self.MEMORY_CAPACITY - current_len,
        }
    
    def add_to_memory(self, entry: MemoryEntry) -> bool:
        """
        向MEMORY.md添加条目
        
        容量管理逻辑（Hermes风格）：
        1. 检查容量是否够
        2. 如果不够 → 先合并/压缩旧的条目
        3. 合并后还不够 → 返回False（拒绝写入）
        
        这是"主动策展"的核心：满了不让加，必须先整理
        """
        # 构造新条目的Markdown文本
        new_text = self._format_memory_entry(entry)
        
        # 容量检查
        current_content = self.read_memory()
        if len(current_content) + len(new_text) > self.MEMORY_CAPACITY:
            # 尝试合并旧的条目
            compressed = self._compress_memory(current_content)
            if len(compressed) + len(new_text) > self.MEMORY_CAPACITY:
                print(f"[Memory] 容量不足！当前{len(current_content)}/{self.MEMORY_CAPACITY}字符，拒绝写入")
                return False
            # 合并成功，更新MEMORY.md
            self._write_memory_file(compressed)
            print(f"[Memory] 合并旧条目后腾出空间，继续写入")
        
        # 追加新条目
        updated = self.read_memory() + new_text
        self._write_memory_file(updated)
        print(f"[Memory] 写入: [{entry.category}] {entry.content[:50]}...")
        return True
    
    def _format_memory_entry(self, entry: MemoryEntry) -> str:
        """格式化一条记忆条目为Markdown"""
        category_icons = {
            "decision": "🎯",
            "lesson": "📚",
            "fact": "📌",
            "preference": "⚙️",
        }
        icon = category_icons.get(entry.category, "📝")
        project_tag = f"({entry.project})" if entry.project else ""
        return f"\n{icon} **[{entry.category}]** {project_tag} {entry.content} — {entry.timestamp}\n"
    
    def _compress_memory(self, content: str) -> str:
        """
        合并/压缩旧记忆条目
        
        简化策略：把旧的同类条目合并成一句话
        比如3条lesson → 1条合并后的lesson
        
        真实场景中应该用LLM做智能合并，这里用规则简化
        """
        lines = content.split("\n")
        entries_by_category: dict[str, list[str]] = {}
        header_lines: list[str] = []
        
        for line in lines:
            if line.startswith("#") or line.strip() == "":
                header_lines.append(line)
                continue
            
            # 解析分类
            for cat in ["decision", "lesson", "fact", "preference"]:
                if f"[{cat}]" in line:
                    entries_by_category.setdefault(cat, []).append(line)
                    break
        
        # 合并：同分类超过3条时，压缩为1条总结
        compressed_lines = header_lines
        for cat, entries in entries_by_category.items():
            if len(entries) > 3:
                # 简化合并：取最新1条 + "另有N条历史记录"
                compressed_lines.append(entries[-1])  # 最新的保留
                compressed_lines.append(f"  └─ 另有{len(entries)-1}条历史{cat}记录已合并")
            else:
                compressed_lines.extend(entries)
        
        return "\n".join(compressed_lines)
    
    # ===== 日志层 =====
    
    def write_daily_log(self, log: DailyLog):
        """
        写入每日日志
        
        格式（参考AGENTS.md的日志格式）：
        ### [PROJECT:名称] 标题
        - **结论**: 一句话总结
        - **文件变更**: 涉及的文件
        - **教训**: 踩坑点
        - **标签**: #tag1 #tag2
        """
        today = date.today().strftime("%Y-%m-%d")
        log_file = self.memory_dir / f"{today}.md"
        
        # 日志格式
        entry = f"""
### [{log.project}] {log.title}
- **结论**: {log.conclusion}
- **文件变更**: {', '.join(log.files_changed)}
- **教训**: {log.lesson}
- **标签**: {' '.join(f'#{t}' for t in log.tags)}
"""
        
        # 如果文件已存在则追加，否则创建
        if log_file.exists():
            existing = log_file.read_text(encoding="utf-8")
            log_file.write_text(existing + entry, encoding="utf-8")
        else:
            log_file.write_text(f"# Daily Log — {today}\n" + entry, encoding="utf-8")
        
        print(f"[Memory] 日志写入: {log_file.name} — [{log.project}] {log.title}")
    
    def read_today_log(self) -> str:
        """读取今日日志"""
        today = date.today().strftime("%Y-%m-%d")
        log_file = self.memory_dir / f"{today}.md"
        if log_file.exists():
            return log_file.read_text(encoding="utf-8")
        return ""
    
    def read_recent_logs(self, days: int = 3) -> list[str]:
        """读取最近N天的日志"""
        logs = []
        for i in range(days):
            d = date.today() - __import__("datetime").timedelta(days=i)
            log_file = self.memory_dir / f"{d.strftime('%Y-%m-%d')}.md"
            if log_file.exists():
                logs.append(log_file.read_text(encoding="utf-8"))
        return logs
    
    # ===== 搜索层 =====
    
    def search(self, keyword: str) -> list[dict]:
        """
        关键词搜索 — 跨MEMORY.md + 日志
        
        真实场景应该用向量检索(memory_search)，这里简化为关键词匹配
        """
        results = []
        
        # 搜索MEMORY.md
        memory_content = self.read_memory()
        if keyword.lower() in memory_content.lower():
            for line in memory_content.split("\n"):
                if keyword.lower() in line.lower() and line.strip():
                    results.append({"source": "MEMORY.md", "content": line.strip()})
        
        # 搜索日志
        for log_file in self.memory_dir.glob("*.md"):
            if log_file.name == "MEMORY.md":
                continue
            content = log_file.read_text(encoding="utf-8")
            if keyword.lower() in content.lower():
                for line in content.split("\n"):
                    if keyword.lower() in line.lower() and line.strip():
                        results.append({"source": log_file.name, "content": line.strip()})
        
        return results
    
    # ===== 工具方法 =====
    
    def _write_memory_file(self, content: str):
        """写入MEMORY.md"""
        self.memory_file.write_text(content, encoding="utf-8")
    
    def __repr__(self):
        cap = self.memory_capacity_info()
        return f"MemoryManager(chars={cap['current_chars']}/{cap['max_chars']}, usage={cap['usage_pct']}%)"


# ===== 测试代码 =====

def test_memory():
    print("=" * 50)
    print("记忆存储模块测试")
    print("=" * 50)
    
    # 用临时目录测试
    import tempfile
    test_dir = tempfile.mkdtemp()
    mm = MemoryManager(test_dir)
    
    # 测试1: 写入长期记忆
    print("\n--- 测试1: 写入长期记忆 ---")
    mm.add_to_memory(MemoryEntry("decision", "使用SQLite而非MySQL做本地开发", "11:00", "mini_harness"))
    mm.add_to_memory(MemoryEntry("lesson", "asyncio.Queue的get()会阻塞，必须用is_processing标志串行化", "11:10", "mini_harness"))
    mm.add_to_memory(MemoryEntry("fact", "Codex的exec_policy.rs有BANNED_PREFIX列表拦截危险命令", "10:30", "agents"))
    mm.add_to_memory(MemoryEntry("preference", "空少喜欢先跑通代码再给下一步", "11:15"))
    
    print(f"\nMEMORY.md内容:\n{mm.read_memory()}")
    print(f"容量状态: {mm.memory_capacity_info()}")
    
    # 测试2: 写入日志
    print("\n--- 测试2: 写入日志 ---")
    mm.write_daily_log(DailyLog(
        project="mini_harness",
        title="Session串行化模块完成",
        conclusion="asyncio.Queue+is_processing标志实现per-session串行化",
        files_changed=["session.py", "run.py"],
        lesson="asyncio.Semaphore控制全局并发上限",
        tags=["session", "async", "queue"]
    ))
    
    mm.write_daily_log(DailyLog(
        project="mini_harness",
        title="权限分级模块完成",
        conclusion="三层分级权限+deny优先+6种模式",
        files_changed=["permissions.py"],
        lesson="dataclass字段顺序要和构造函数参数顺序一致，否则session_id会赋给timestamp",
        tags=["permissions", "deny-first", "claude-code"]
    ))
    
    print(f"今日日志:\n{mm.read_today_log()}")
    
    # 测试3: 搜索
    print("\n--- 测试3: 搜索 ---")
    results = mm.search("串行化")
    for r in results:
        print(f"  [{r['source']}] {r['content']}")
    
    results = mm.search("deny")
    for r in results:
        print(f"  [{r['source']}] {r['content']}")
    
    # 测试4: 容量管理（写满MEMORY.md）
    print("\n--- 测试4: 容量管理 ---")
    # 大量写入直到容量不够
    for i in range(20):
        success = mm.add_to_memory(MemoryEntry("fact", f"测试条目{i}: 这是一段比较长的内容用来测试容量管理机制是否正常工作", "12:00"))
        if not success:
            print(f"  条目{i}: 写入被拒绝 ✅ 容量管理生效")
            break
    
    print(f"最终容量状态: {mm}")
    print(f"MEMORY.md内容（前300字符）:\n{mm.read_memory()[:300]}...")
    
    # 清理
    import shutil
    shutil.rmtree(test_dir)
    print("\n测试完毕，临时目录已清理")


if __name__ == "__main__":
    test_memory()