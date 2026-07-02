"""
长期记忆：跨session持久化（索引层 + 详情层）
核心功能：
1. MEMORY.md索引（精简<40行，快速定位）
2. memory/*.md详情文件（按需拉取完整信息）
3. 语义检索（关键词匹配 + 粗略向量模拟）

类比：人的"知识库"，跨session永久保留
Java直觉：索引层=数据库主键索引（精确定位），详情层=ES全文检索（模糊搜索）
关键设计：先查索引精确定位 → 再用详情层拉取完整信息
"""

import json
import os
import time

class LongTermMemory:
    """长期记忆：跨session持久化的知识存储"""

    def __init__(self, memory_dir="memory"):
        self.memory_dir = memory_dir
        self.index_file = os.path.join(memory_dir, "MEMORY.md")
        os.makedirs(memory_dir, exist_ok=True)
    
    # ---- 索引层：MEMORY.md（精简<40行） ----

    def read_index(self):
        """读取索引文件，返回所有条目"""
        if not os.path.exists(self.index_file):
            return []
        with open(self.index_file, "r") as f:
            content = f.read()
        # 解析索引条目：每行格式 "## 标题 | 标签: #tag1 #tag2 | 文件: filename.md"
        entries = []
        for line in content.split("\n"):
            line = line.strip()
            if not line or line.startswith("#this"):
                continue
            if line.startswith("##") or line.startswith("###"):
                # 标题行
                entries.append({"type": "heading", "content": line})
            elif line.startswith("-") or line.startswith("*"):
                # 条目行：- **结论**: xxx → 解析
                entries.append({"type": "item", "content": line})
            elif line.startswith("|"):
                # 表格行
                entries.append({"type": "table", "content": line})
            else:
                entries.append({"type": "text", "content": line})
        return entries

    def search_index(self, query):
        """索引层检索：关键词匹配（第一层过滤）"""
        entries = self.read_index()
        query_lower = query.lower()
        results = []
        for entry in entries:
            if query_lower in entry["content"].lower():
                results.append(entry)
        return results

    def update_index(self, heading, content, tags, detail_file):
        """
        向索引添加新条目
        
        Args:
            heading: 标题（如"MySQL charset双重编码坑"）
            content: 精简结论（1行，如"结论：Docker exec默认latin1导致双重编码"）
            tags: 标签列表（如["mysql", "charset", "docker"]）
            detail_file: 详情文件名（如"2026-06-01.md"）
        """
        # 构建索引条目
        tag_str = " ".join(f"#tag_{t}" for t in tags)
        entry_line = f"- **{heading}**: {content} | {tag_str} | 详情→{detail_file}"

        # 读取现有索引
        existing = ""
        if os.path.exists(self.index_file):
            with open(self.index_file, "r") as f:
                existing = f.read()

        # 追加新条目
        with open(self.index_file, "w") as f:
            f.write(existing.rstrip() + "\n\n" + entry_line + "\n")

        print(f"[长期记忆] 索引更新: {heading}")
        
    # ---- 详情层：memory/*.md ----

    def write_detail(self, filename, content):
        """写入详情文件"""
        filepath = os.path.join(self.memory_dir, filename)
        with open(filepath, "w") as f:
            f.write(content)
        print(f"[长期记忆] 详情写入: {filename}")

    def read_detail(self, filename):
        """读取详情文件"""
        filepath = os.path.join(self.memory_dir, filename)
        if not os.path.exists(filepath):
            return None
        with open(filepath, "r") as f:
            return f.read()

    def search_details(self, query):
        """详情层检索：遍历所有详情文件做关键词匹配"""
        query_lower = query.lower()
        results = []
        for filename in os.listdir(self.memory_dir):
            if not filename.endswith(".md") or filename == "MEMORY.md":
                continue
            content = self.read_detail(filename)
            if content and query_lower in content.lower():
                # 找到匹配的详情，返回前200字预览
                preview = content[:200]
                results.append({
                    "file": filename,
                    "preview": preview,
                    "full": content,
                })
        return results
    
    # ---- 级联检索：索引 → 详情 ----

    def query(self, query):
        """
        级联检索：先查索引（精确定位）→ 再查详情（模糊补充）
        
        这就是为什么MEMORY.md要精简<40行：
        索引小=查得快，索引大=查得慢而且容易匹配到无关条目
        """
        print(f"[长期记忆] 查询: '{query}'")

        # 第一层：索引检索（快速定位）
        index_results = self.search_index(query)
        print(f"  索引层命中: {len(index_results)}条")

        # 从索引结果中提取详情文件引用
        detail_refs = []
        for entry in index_results:
            if "详情→" in entry["content"]:
                ref = entry["content"].split("详情→")[-1].strip()
                detail_refs.append(ref)

        # 第二层：拉取详情文件
        detail_results = []
        for ref in detail_refs:
            content = self.read_detail(ref)
            if content:
                detail_results.append({
                    "file": ref,
                    "content": content,
                    "source": "index→detail",
                })

        # 第三层：如果索引没命中，直接搜详情文件（兜底）
        if not detail_results:
            direct_results = self.search_details(query)
            print(f"  索引未命中，直接搜详情: {len(direct_results)}条")
            detail_results = direct_results

        print(f"  最终结果: {len(detail_results)}条详情")
        return detail_results

    def debug_print(self):
        """调试：打印索引状态"""
        print(f"\n=== 长期记忆状态 ===")
        print(f"目录: {self.memory_dir}")
        entries = self.read_index()
        print(f"索引条目数: {len(entries)}")
        detail_files = [f for f in os.listdir(self.memory_dir)
                        if f.endswith(".md") and f != "MEMORY.md"]
        print(f"详情文件数: {len(detail_files)}")
        print(f"详情文件列表: {detail_files}")
        print("===================\n")


# ===== 测试代码 =====
if __name__ == "__main__":
    print("=== 镟期记忆测试 ===\n")

    # 使用独立测试目录，避免污染真实记忆
    test_dir = "/tmp/test_long_term_memory/memory"
    os.makedirs(test_dir, exist_ok=True)
    ltm = LongTermMemory(memory_dir=test_dir)

    # 1. 写入几条学习经验（模拟Agent自学习提炼）
    ltm.update_index(
        heading="MySQL charset双重编码",
        content="结论：Docker exec默认latin1，WHERE匹配不到中文",
        tags=["mysql", "charset", "docker", "rag"],
        detail_file="2026-06-01.md"
    )

    ltm.write_detail("2026-06-01.md",
        "# MySQL charset踩坑\n\n"
        "## 问题\nDocker exec进入MySQL容器，默认charset是latin1不是utf8mb4\n"
        "导致中文数据双重编码，WHERE clause匹配不到\n\n"
        "## 解决\n```bash\ndocker exec -it llm-mysql mysql -uroot -pllm_learn_2026 "
        "--default-character-set=utf8mb4 llm_learn\n```\n\n"
        "## 教训\n涉及中文的数据库必须全程charset=utf8mb4，从连接到存储到查询"
    )

    ltm.update_index(
        heading="Agent上下文膨胀",
        content="结论：多Agent串行时中间输出塞进变量导致token爆炸",
        tags=["agent", "token", "context", "workflow"],
        detail_file="2026-05-31.md"
    )

    ltm.write_detail("2026-05-31.md",
        "# Agent上下文膨胀问题\n\n"
        "## 问题\nCodeReviewer输出1800字塞进{{codeReview}}变量\n"
        "SecurityScanner读这个变量时token爆炸，第一次504超时\n\n"
        "## 解决\n1. Agent间传递用JSON而非自由文本\n2. 每个Agent只读需要的key\n"
        "3. 限制单Agent输出max_tokens\n\n"
        "## 教训\n多Agent串行的隐形杀手是上下文膨胀，不是代码逻辑"
    )

    ltm.update_index(
        heading="OOD检测threshold陷阱",
        content="结论：threshold=0.4太低导致知识库内问题误判为OOD",
        tags=["rag", "ood", "threshold", "误判"],
        detail_file="2026-05-21.md"
    )

    ltm.write_detail("2026-05-21.md",
        "# OOD检测threshold陷阱\n\n"
        "## 问题\nOOD threshold设为0.4太低\n"
        "知识库内的保险问题(相似度0.42)被误判为OOD拒答\n\n"
        "## 解决\nthreshold调到0.5，拒答率从40%降到5%\n\n"
        "## 教训\nOOD检测概念正确但threshold需谨慎调优"
    )

    ltm.debug_print()

    # 2. 级联检索测试
    print("--- 查询1：精确命中索引 ---")
    results = ltm.query("charset")
    for r in results:
        print(f"  来源: {r.get('source', 'detail')}")
        print(f"  文件: {r['file']}")
        print(f"  内容前100字: {r['content'][:100]}...")
        print()

    print("--- 查询2：索引未命中，兜底搜详情 ---")
    results = ltm.query("token爆炸")
    for r in results:
        print(f"  来源: {r.get('source', 'detail')}")
        print(f"  文件: {r['file']}")
        print(f"  内容前100字: {r['content'][:100]}...")
        print()

    print("--- 查询3：完全没命中 ---")
    results = ltm.query("量子计算")
    print(f"  结果数: {len(results)}")

    print("\n=== 核心洞察 ===")
    print("1. 索引层精确定位（keyword匹配MEMORY.md）→ 详情层拉取完整信息")
    print("2. 索引没命中时兜底搜详情文件（但不如索引精确）")
    print("3. MEMORY.md必须精简<40行——索引小=查得快，索引大=噪声多")
    print("4. 真实生产用向量库替代关键词匹配——语义检索比keyword更智能")
    print("5. 紧急情况可以直接搜详情（如查询'token爆炸'索引没有但详情有）")