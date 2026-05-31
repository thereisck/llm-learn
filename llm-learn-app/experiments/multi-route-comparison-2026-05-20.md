# 多路召回 vs 单路检索对比实验

**日期**: 2026-05-20
**知识库**: docs/rag-note.md（12 chunks）
**测试接口**: /rag/search（只做检索不调LLM）
**对比模式**: vector / bm25 / hybrid / hybrid_rerank / multi_route

---

## 实验设计

| 问题ID | 问题 | 测试目标 |
|--------|------|----------|
| Q1 | 怎么让知识库问答少答错一点 | 语义相似（测向量路） |
| Q2 | BM25的k1参数默认值是多少 | 精确术语（测BM25路） |
| Q3 | Rerank的Cross-Encoder和向量检索有什么区别 | 混合意图（测Hybrid+Rerank） |
| Q4 | spring.datasource.url配置 | 精确代码标识符（测exactMatch路） |
| Q5 | 如何在北京注册一家公司 | 知识库外无关问题（测误召回控制） |

---

## 核心发现

### 1. multi_route ≈ hybrid_rerank，没有显著优势

| 问题 | hybrid_rerank top chunk | multi_route top chunk | 差异 |
|------|------------------------|----------------------|------|
| Q1 | #3 (0.9623) | #3 (0.9629) | score几乎一致 |
| Q2 | #9 (0.6808) | #9 (0.6789) | score几乎一致 |
| Q3 | #6 (0.998) | #6 (0.998) | 完全一致 |
| Q4 | #5 (0.2729) | #5 (0.2729) | 完全一致 |
| Q5 | #3 (0.0004) | #3 (0.0004) | 完全一致 |

**结论**: 在单文档小知识库场景下，第三路（exactMatch）的贡献为零——所有 multi_route 的 top1 结果和 hybrid_rerank 完全相同。这是因为：
- exactMatch 召回的 chunk 和 vector/bm25 召回的 chunk 高度重叠
- 小知识库（12 chunks）中，向量+BM25两路已经覆盖了几乎所有可能命中的chunk
- Rerank 精排后，多路带来的额外候选都被 Rerank 剔除了

### 2. 各路擅长的场景确实不同

| 问题类型 | vector | bm25 | hybrid_rerank |
|----------|--------|------|---------------|
| 语义相似(Q1) | #3(0.71) ✅ | #3(53) ✅ | #3(0.96) ✅✅ |
| 精确术语(Q2) | #5(0.72) ❌没命中k1 | #9(42) ✅命中BM25详解 | #9(0.68) ✅ |
| 混合意图(Q3) | #6(0.82) ✅ | #9(24) 偏了 | #6(0.998) ✅✅✅ |
| 精确标识符(Q4) | #11(0.51) ❌只1条 | #5(5.3) ✅命中配置项内容 | #5(0.27) ✅ |
| 无关问题(Q5) | 0条 ✅✅✅ | 3条 ❌误召回 | 3条(0.0004) ⚠️极低score |

**关键洞察**：
- **向量检索的误召回控制最强**（Q5返回0条），因为语义不相关的向量距离远，threshold 直接过滤掉
- **BM25 最容易误召回**（Q5返回3条），因为中文分词的 unigram/bigram 太短，几乎所有chunk都包含某个单字
- **Rerank 是最好的兜底**——即使粗筛误召回，Rerank 给的 score 极低（0.0004），可以加 Rerank score threshold 进一步过滤

### 3. 性能对比

| 模式 | 平均耗时 | 说明 |
|------|----------|------|
| bm25 | ~7ms | 纯本地计算，最快 |
| vector | ~200ms | 需调embedding API |
| hybrid | ~190ms | embedding+BM25本地 |
| hybrid_rerank | ~400ms | embedding+BM25+Rerank API |
| multi_route | ~340ms | embedding+BM25+exactMatch+Rerank API |

multi_route 比 hybrid_rerank 略快，因为 exactMatch 路是纯本地计算，但差别不大。

### 4. exactMatch路的问题

Q4是专门为exactMatch设计的问题（"spring.datasource.url配置"），但：
- vector 只召回了1条（chunk#11，score=0.51），但内容是Embedding模型对比，不是spring配置
- bm25 命中了chunk#5（包含"spring.datasource.url"原文），score=5.26
- exactMatch 也命中了chunk#5（包含"spring.datasource.url"），score较高
- **但最终 multi_route 的 top1 和 hybrid_rerank 一样**——因为 Rerank 精排时只看语义相关性，不看是否精确包含关键词

**这是一个重要教训**：Rerank 模型（Cross-Encoder）是语义精排，不是关键词精排。如果精确匹配对场景很重要（如代码搜索、配置项搜索），应该在 Rerank 之前/之后加一个关键词匹配加权环节，而不是依赖 Rerank 来保精确匹配。

---

## 总结

### 在单文档小知识库场景（<1000 chunks）

**multi_route 不值得加第三路**。原因：
1. 向量+BM25两路已经覆盖了绝大多数可能命名的chunk
2. exactMatch 召回的chunk和两路重叠
3. Rerank 精排后，多路带来的额外候选被剔除
4. 多路只增加了复杂度，没有增加精度

### 在什么时候多路召回有价值？

1. **多知识源场景**：不同知识库（产品文档、API文档、FAQ），每路检索不同源
2. **大规模知识库**（>10万chunks）：向量检索可能召回太分散，需要按领域/标签分路
3. **特定领域术语密集**：法律条文编号、医疗术语编码、金融指标名——这些需要专门的精确匹配路，且**不应交给 Rerank 重新排序**，应保留原始排序权重

### 实践建议

- **小规模场景**：hybrid_rerank 就够了，简单、稳定、精度高
- **中大规模场景**：hybrid_rerank 作为默认，特定问题类型加路
- **精排策略调整**：如果精确匹配很重要，Rerank 后按关键词命中数做二次加权，不要纯靠 Rerank score
- **误召回控制**：加 Rerank score threshold（如 <0.3 视为不相关），比向量 threshold 更有效

---

## 实验原始数据

详见 `multi-route-comparison-2026-05-20.json`