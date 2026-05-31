# Hybrid Search vs Hybrid + Rerank 对比实验

**日期**: 2026-05-17
**语料**: docs/rag-note.md（13 chunks）
**模型**: bge-reranker-v2-m3（SiliconFlow API）

---

## 实验1：语义问题 — "Rerank的Cross-Encoder和Bi-Encoder有什么区别"

### hybrid（无Rerank）

| chunk | score | 前50字 |
|-------|-------|--------|
| 6 | 0.032266 | 题类型适合不同检索策略。##Rerank重排序详解... |
| 10 | 0.032002 | 混合意图问题比如"Rerank的Cross-Encoder... |
| 7 | 0.031754 | Query和Document在编码时互相不知道... |

**排序问题**: chunk 6（Rerank概念介绍）排第一，chunk 7（Cross-Encoder详解）排第三。真正的核心答案chunk被排到了后面。

### hybrid_rerank（有Rerank）

| chunk | score | 前50字 |
|-------|-------|--------|
| 7 | 0.997471 | Query和Document在编码时互相不知道对方的存在...（Cross-Encoder详解） |
| 6 | 0.996318 | Rerank重排序详解... |
| 9 | 0.941348 | bge-reranker-v2-m3... |

**关键变化**:
- ✅ chunk 7（最直接回答问题的）被Rerank精排到了第一位！
- ✅ score 从 RRF的0.032 → Cross-Encoder的0.997，分数尺度更直观
- ✅ 干扰项 chunk 10 被剔除，换成更有价值的 chunk 9（Rerank模型介绍）

---

## 实验2：精确匹配问题 — "BM25的k1参数默认值是多少"

### hybrid（无Rerank）

| chunk | score | 前50字 |
|-------|-------|--------|
| 2 | 0.031498 | 专有名词；向量检索擅长语义相似... |
| 9 | 0.031319 | bge-reranker-v2-m3... |
| 4 | 0.031054 | RRF公式... |

**结果**: 回答"资料中没有足够信息"——因为召回的chunk都不包含k1的具体默认值（语料中只在Bm25Searcher.java的代码注释里有，不在rag-note.md中）

### hybrid_rerank（有Rerank）

| chunk | score | 前50字 |
|-------|-------|--------|
| 9 | 0.679859 | bge-reranker-v2-m3... |
| 4 | 0.078179 | RRF公式... |
| 3 | 0.061615 | Qwen3-Embedding、SKU... |

**结果**: 同样回答"资料中没有足够信息"

**分析**: 这个问题两种模式都无法回答，因为语料中确实没有k1默认值的内容。但Rerank至少把提到"BM25的k1参数"这个例子的chunk 9精排到了第一位，score差异明显。

---

## 总结

| 维度 | hybrid | hybrid_rerank |
|------|--------|---------------|
| 分数尺度 | RRF分数极小(0.03x) | Cross-Encoder分数直观(0.6~0.99) |
| 排序精确度 | 粗筛排第一的不一定最相关 | 最直接回答问题的chunk排到第一 |
| 干扰项剔除 | 干扰chunk可能留在Top3 | Rerank自动剔除低相关chunk |
| 耗时增量 | ~2s | ~3s（增加Rerank API调用） |

**结论**: Rerank的核心价值在于"精排"——把最直接回答用户问题的chunk从粗筛结果中找出来并排到前面。分数从0.03x变成0.99x也更直观。对于语义复杂问题效果最明显。