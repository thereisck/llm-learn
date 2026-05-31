#!/usr/bin/env python3
"""
语义切分实验对比脚本
对比 Fixed / Recursive / Semantic(THRESHOLD/DIFF/PERCENTILE) 切分效果
"""

import json
import requests
import time

BASE_URL = "http://localhost:8900"

# 用 BM25 讲义作为实验文档（有明确的语义段落）
DOC_PATH = "/Users/zhiweizhang/Downloads/aicc/workspace/llm-learn/src/main/resources/docs/rag/bm25-introduction.md"

with open(DOC_PATH, "r") as f:
    TEST_TEXT = f.read()

print(f"文档长度: {len(TEST_TEXT)} 字符")
print("=" * 60)


# ========== 1. Fixed 切分（本地模拟） ==========
def fixed_split(text, chunk_size=500, overlap=100):
    normalized = text.replace("\n", " ").strip()
    chunks = []
    start = 0
    while start < len(normalized):
        end = min(start + chunk_size, len(normalized))
        chunks.append(normalized[start:end])
        if end == len(normalized):
            break
        start = end - overlap
    return chunks


fixed_chunks = fixed_split(TEST_TEXT, chunk_size=500, overlap=100)
print(f"\n📌 Fixed 切分 (chunk_size=500, overlap=100)")
print(f"   Chunk 数量: {len(fixed_chunks)}")
for i, c in enumerate(fixed_chunks[:3]):
    print(f"   Chunk {i}: {c[:80]}... (长度={len(c)})")
if len(fixed_chunks) > 3:
    print(f"   ... (共 {len(fixed_chunks)} 个)")


# ========== 2. Recursive 切分（本地模拟） ==========
def recursive_split(text, separators=None):
    if separators is None:
        separators = ["\n\n", "\n", "。", "！", "？", " "]
    
    final_chunks = []
    current_text = text
    
    for sep in separators:
        if not current_text:
            break
        parts = current_text.split(sep)
        good_parts = []
        remaining = []
        
        for part in parts:
            p = part.strip()
            if len(p) >= 10:  # 过滤太短的片段
                good_parts.append(p)
            else:
                remaining.append(p)
        
        final_chunks.extend(good_parts)
        current_text = sep.join(remaining) if remaining else ""
    
    # 处理残余
    if current_text.strip() and len(current_text.strip()) >= 10:
        final_chunks.append(current_text.strip())
    
    return final_chunks


recursive_chunks = recursive_split(TEST_TEXT)
print(f"\n📌 Recursive 切分")
print(f"   Chunk 数量: {len(recursive_chunks)}")
for i, c in enumerate(recursive_chunks[:3]):
    print(f"   Chunk {i}: {c[:80]}... (长度={len(c)})")
if len(recursive_chunks) > 3:
    print(f"   ... (共 {len(recursive_chunks)} 个)")


# ========== 3. Semantic 切分（调用 API） ==========
def semantic_chunk(text, strategy, param):
    try:
        resp = requests.post(
            f"{BASE_URL}/rag/chunk/semantic",
            json={"text": text, "strategy": strategy, "param": param},
            timeout=120
        )
        if resp.status_code == 200:
            return resp.json()
        else:
            print(f"   ❌ API 错误: {resp.status_code} - {resp.text[:200]}")
            return []
    except Exception as e:
        print(f"   ❌ 连接失败: {e}")
        return []


print("\n📌 Semantic 切分 - PERCENTILE (25.0)")
print("   (需要调用 Embedding API，可能需要 30-60 秒)")
semantic_p25 = semantic_chunk(TEST_TEXT, "PERCENTILE", 25.0)
if semantic_p25:
    print(f"   Chunk 数量: {len(semantic_p25)}")
    for i, c in enumerate(semantic_p25[:3]):
        print(f"   Chunk {i}: {c[:80]}... (长度={len(c)})")
    if len(semantic_p25) > 3:
        print(f"   ... (共 {len(semantic_p25)} 个)")

time.sleep(2)

print("\n📌 Semantic 切分 - THRESHOLD (0.5)")
semantic_t05 = semantic_chunk(TEST_TEXT, "THRESHOLD", 0.5)
if semantic_t05:
    print(f"   Chunk 数量: {len(semantic_t05)}")
    for i, c in enumerate(semantic_t05[:3]):
        print(f"   Chunk {i}: {c[:80]}... (长度={len(c)})")
    if len(semantic_t05) > 3:
        print(f"   ... (共 {len(semantic_t05)} 个)")

time.sleep(2)

print("\n📌 Semantic 切分 - DIFF (0.3)")
semantic_d03 = semantic_chunk(TEST_TEXT, "DIFF", 0.3)
if semantic_d03:
    print(f"   Chunk 数量: {len(semantic_d03)}")
    for i, c in enumerate(semantic_d03[:3]):
        print(f"   Chunk {i}: {c[:80]}... (长度={len(c)})")
    if len(semantic_d03) > 3:
        print(f"   ... (共 {len(semantic_d03)} 个)")


# ========== 4. 汇总对比 ==========
print("\n" + "=" * 60)
print("📊 切分对比汇总")
print("=" * 60)

results = {
    "Fixed (500/100)": {"count": len(fixed_chunks), "avg_len": sum(len(c) for c in fixed_chunks) / len(fixed_chunks)},
    "Recursive": {"count": len(recursive_chunks), "avg_len": sum(len(c) for c in recursive_chunks) / len(recursive_chunks)},
    "Semantic-P25": {"count": len(semantic_p25) if semantic_p25 else 0, "avg_len": sum(len(c) for c in semantic_p25) / len(semantic_p25) if semantic_p25 else 0},
    "Semantic-T0.5": {"count": len(semantic_t05) if semantic_t05 else 0, "avg_len": sum(len(c) for c in semantic_t05) / len(semantic_t05) if semantic_t05 else 0},
    "Semantic-D0.3": {"count": len(semantic_d03) if semantic_d03 else 0, "avg_len": sum(len(c) for c in semantic_d03) / len(semantic_d03) if semantic_d03 else 0},
}

print(f"\n| 方法 | Chunk数 | 平均长度 |")
print(f"|------|---------|----------|")
for method, data in results.items():
    print(f"| {method} | {data['count']} | {data['avg_len']:.0f} |")

# ========== 5. 语义完整性检查 ==========
print("\n📋 语义完整性抽查（看前3个chunk的开头和结尾）")
print("-" * 40)

for method, chunks in [
    ("Fixed", fixed_chunks),
    ("Recursive", recursive_chunks),
    ("Semantic-P25", semantic_p25 if semantic_p25 else []),
]:
    if not chunks:
        continue
    print(f"\n🔍 {method}:")
    for i in range(min(3, len(chunks))):
        start_text = chunks[i][:50]
        end_text = chunks[i][-50:]
        # 检查是否在句子中间断裂
        broken_start = not start_text.startswith(("#", "一", "二", "三", "你", "跟", "例", "BM", "假", "有", "搜", "一", "TF", "ID", "对", "长", "看", "B"))
        broken_end = not end_text.endswith(("。", "！", "？", "：", ")", "…", ">", "|"))
        flag = "⚠️ 可能断裂" if (broken_start and broken_end) else "✅ 完整"
        print(f"   Chunk {i}: 开头='{start_text}...' | 结尾='...{end_text}' | {flag}")

# ========== 6. 保存实验结果 ==========
output = {
    "document": "bm25-introduction.md",
    "doc_length": len(TEST_TEXT),
    "results": {}
}

for method, chunks in [
    ("fixed_500_100", fixed_chunks),
    ("recursive", recursive_chunks),
    ("semantic_percentile_25", semantic_p25 if semantic_p25 else []),
    ("semantic_threshold_0.5", semantic_t05 if semantic_t05 else []),
    ("semantic_diff_0.3", semantic_d03 if semantic_d03 else []),
]:
    output["results"][method] = {
        "chunk_count": len(chunks),
        "avg_chunk_length": sum(len(c) for c in chunks) / len(chunks) if chunks else 0,
        "chunks": chunks
    }

output_path = "/Users/zhiweizhang/Downloads/aicc/workspace/llm-learn/experiments/chunk-comparison-2026-05-19.json"
with open(output_path, "w") as f:
    json.dump(output, f, ensure_ascii=False, indent=2)

print(f"\n✅ 实验结果已保存到: {output_path}")