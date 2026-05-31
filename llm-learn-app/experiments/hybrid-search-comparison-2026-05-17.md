# Hybrid Search 对比实验（BM25 vs Vector vs Hybrid）

## 实验背景

本实验基于 `llm-learn` 项目的本地 RAG 服务，对比三种检索模式：

- `vector`：纯向量检索，基于 Embedding + cosine similarity。
- `bm25`：关键词检索，基于手写 BM25 倒排索引。
- `hybrid`：混合检索，先分别执行 vector 与 BM25，再用 RRF（Reciprocal Rank Fusion）融合排名。

实验目的不是证明某一种模式永远最好，而是观察不同问题类型下各检索策略的偏好。

## 实验配置

| 配置项 | 值 |
|---|---|
| 文档 | `src/main/resources/docs/rag-note.md` |
| chunk-size | 500 |
| overlap | 100 |
| top-k | 3 |
| vector threshold | 0.5 |
| hybrid candidateSize | `max(topK * 5, 20)` |
| hybrid fusion | RRF, `k=60` |
| 启动日志 | `chunk数量: 6, bm25文档数量: 6` |

> 注意：Hybrid 模式下返回的 `score` 是 RRF 融合分，不是 cosine 分数，也不是 BM25 分数，因此不能和另外两种模式的分数直接比较。

## 实验问题与结果

### Q1：精确术语 / 编号类问题

**问题**：SKU-20260516 这种业务编号适合用什么检索方式？

**预期**：BM25 应命中包含 SKU-20260516 / OrderService / NullPointerException 的语料；hybrid 应稳定召回。

| 模式 | Top1 | Top1 内容摘要 | 命中关键词 | 观察 |
|---|---|---|---|---|
| vector | chunk 3 / score=0.527415 | 、Qwen3-Embedding、SKU-20260516、OrderService、NullPointerException 这类专有名词、英文缩写、代码标识符或者业务编号时，关... | SKU-20260516, OrderService, NullPointerException, 业务编号, 关键词检索 | 向量检索也命中关键语料，但 score 接近阈值，说明依赖语义相似度时稳定性一般。 |
| bm25 | chunk 3 / score=13.905152 | 、Qwen3-Embedding、SKU-20260516、OrderService、NullPointerException 这类专有名词、英文缩写、代码标识符或者业务编号时，关... | SKU-20260516, OrderService, NullPointerException, 业务编号, 关键词检索 | 精确命中 SKU / OrderService / NPE 等符号，BM25 分数最高，符合预期。 |
| hybrid | chunk 2 / score=0.032522 | 专有名词；向量检索擅长语义相似问题。两者结合通常比单纯向量检索更稳。 Rerank 是检索后的重排序步骤。第一阶段可以召回较多候选片段，比如 top20；第二阶段用 reranke... | SKU-20260516, OrderService, NullPointerException, 业务编号, 关键词检索 | 融合后 chunk2 与 chunk3 并列靠前，稳定保留精确符号相关语料。 |

**小结**：精确编号/代码符号类查询是 BM25 的优势场景。Vector 也可能召回，但稳定性依赖 embedding 对符号上下文的理解；Hybrid 能把 BM25 的精确命中纳入最终结果，适合作为默认生产策略。

### Q2：语义模糊问题

**问题**：怎么让知识库问答少答错一点？

**预期**：vector 应更容易召回减少幻觉、优化检索链路、提升准确率相关语料；bm25 可能依赖泛词命中。

| 模式 | Top1 | Top1 内容摘要 | 命中关键词 | 观察 |
|---|---|---|---|---|
| vector | chunk 3 / score=0.688071 | 、Qwen3-Embedding、SKU-20260516、OrderService、NullPointerException 这类专有名词、英文缩写、代码标识符或者业务编号时，关... | 减少幻觉, 优化检索链路, 提升 RAG 准确率, 资料没召回, Prompt | Top1 命中“少答错 / 减少幻觉 / 优化检索链路”语义片段，符合语义检索主场。 |
| bm25 | chunk 3 / score=40.447886 | 、Qwen3-Embedding、SKU-20260516、OrderService、NullPointerException 这类专有名词、英文缩写、代码标识符或者业务编号时，关... | 减少幻觉, 优化检索链路, 提升 RAG 准确率, Prompt | 由于实验语料里出现了与问题高度相同的句子，BM25 也强命中；这说明语料设计会影响对比。 |
| hybrid | chunk 3 / score=0.032787 | 、Qwen3-Embedding、SKU-20260516、OrderService、NullPointerException 这类专有名词、英文缩写、代码标识符或者业务编号时，关... | 减少幻觉, 优化检索链路, 提升 RAG 准确率, 资料没召回, Prompt | 融合后仍把语义核心 chunk3 放到第一，并补入 chunk2 的 RAG 问题归因。 |

**小结**：语义模糊问题通常是 Vector 的优势场景。本实验中 BM25 也表现很好，是因为补充语料中直接包含“怎么让知识库问答少答错一点”这句话；如果改成更自然的同义改写，Vector 与 Hybrid 的优势会更明显。

### Q3：混合型问题

**问题**：BM25 和 RRF 在混合检索里分别解决什么问题？

**预期**：hybrid 应融合 BM25/RRF 精确关键词命中和混合检索语义相关语料。

| 模式 | Top1 | Top1 内容摘要 | 命中关键词 | 观察 |
|---|---|---|---|---|
| vector | chunk 5 / score=0.769765 | 态码”。前者更适合向量检索，后者更适合关键词检索。如果系统只支持向量检索，编号、函数名、错误码、配置项、API 路径这类精确符号很容易被忽略。 在 Java 后端项目中，用户可能会... | BM25, RRF, Hybrid Search, 混合检索, 互倒数排名融合 | Top1 偏向“精确符号容易被忽略”的语义段，能回答但不是最直接解释 RRF 的片段。 |
| bm25 | chunk 3 / score=8.633410 | 、Qwen3-Embedding、SKU-20260516、OrderService、NullPointerException 这类专有名词、英文缩写、代码标识符或者业务编号时，关... | BM25, RRF, Hybrid Search, 混合检索, 互倒数排名融合 | 强命中 BM25/RRF/混合检索关键词段，适合这种术语型问题。 |
| hybrid | chunk 3 / score=0.032266 | 、Qwen3-Embedding、SKU-20260516、OrderService、NullPointerException 这类专有名词、英文缩写、代码标识符或者业务编号时，关... | BM25, RRF, Hybrid Search, 混合检索, 互倒数排名融合 | 融合后 chunk3、chunk4、chunk5 同时进入 top3，兼顾关键词与语义覆盖。 |

**小结**：混合型问题同时包含术语和语义意图。BM25 擅长锁定 `BM25/RRF` 关键词，Vector 擅长理解“分别解决什么问题”的语义，Hybrid 通过 RRF 把两路靠前结果合并，覆盖更完整。

## 综合结论

1. **BM25 的核心价值是精确匹配兜底**：对于 `SKU-20260516`、`OrderService`、`NullPointerException`、配置项、API 路径这类符号型查询，BM25 比纯向量检索更可控。
2. **Vector 的核心价值是语义泛化**：当用户表达不是文档原词时，向量检索更容易召回语义相关片段。
3. **Hybrid 的价值不是“分数更高”，而是“召回更稳”**：RRF 不比较 cosine 与 BM25 的原始分数，只根据排名融合，避免了分数归一化的麻烦。
4. **实验暴露了一个语料设计问题**：Q2 的问题句在语料中近似原文出现，导致 BM25 也非常强。后续若要更准确验证语义检索优势，应该增加更多“同义改写但不复用原词”的问题。
5. **当前实现已具备最小可用闭环**：`vector/bm25/hybrid` 三种模式都能返回 sources，Hybrid 的 RRF 分数约在 `0.03` 附近，这是正常现象。

## 关键修正记录

实验前发现 `hybridSearch()` 中最后错误返回了 `bm25Searcher.search(question, limit)`，这会导致 `hybrid` 实际退化为 `bm25`。已修正为：

```java
return reciprocalRankFusion(vectorResults, bm25Results, limit);
```

否则本实验结论会失真。

## 原始结果

完整 JSON 原始响应见：`experiments/hybrid-search-raw-2026-05-17.json`
