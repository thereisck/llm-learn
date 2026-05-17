# RAG Threshold 实验结论（2026-05-13）

## 实验环境

- 项目：`/Users/zhiweizhang/Downloads/aicc/workspace/llm-learn`
- 接口：`POST http://localhost:8900/rag/query`
- Body：`{"question":"...","threshold":0.0}`
- 文档：`src/main/resources/docs/rag-note.md`
- 问句：10 个
- 阈值：0.20 / 0.35 / 0.50 / 0.65 / 0.80
- 原始数据：`experiments/rag-threshold-raw-2026-05-13.jsonl`
- 汇总表：`experiments/rag-threshold-summary-2026-05-13.md`

## 核心结论

### 1. 当前最稳的 threshold 起点是 0.50，不是 0.35

`0.20 / 0.35 / 0.50` 三档全部问题都召回 3 个 chunk，说明在这份文档和当前 embedding 模型下，0.35 并没有明显过滤效果。

如果只看知识库内问题，0.50 仍然能保持完整召回；如果继续升到 0.65，部分问题开始丢关键 chunk。

建议默认值：

```yaml
threshold: 0.50
```

### 2. 0.65 已经开始误伤有效召回

典型误伤：

- A2：`topK 和 threshold 分别控制什么？`
  - 0.65 只召回 chunk 2，反而漏掉真正讲 topK/threshold 的 chunk 1，导致回答“资料中没有足够信息”。
- B2：`文档切得太碎会有什么问题？`
  - 0.65 只召回 chunk 2，漏掉 chunk 0，导致回答失败。
- C2：`关键词检索和向量检索各自擅长什么？`
  - 0.65 只召回 chunk 2，信息变窄。

结论：

```text
0.65 可以用于高精度场景，但不适合作为当前 demo 的默认阈值。
```

### 3. 0.80 过高，直接让系统失明

0.80 下 10 个问题里 9 个零召回，只有 Rerank 问题还能召回 1 个 chunk。

这不是“更严谨”，这是“召回系统失效”。

```text
threshold = 0.80 不适合当前 embedding + chunk 设置。
```

### 4. 知识库外问题暴露了一个关键事实：threshold 单独挡不住伪相关

两个知识库外问题：

- D1：`RAG 系统如何做用户权限隔离？`
- D2：`如何用 Redis 缓存 RAG 检索结果？`

在 0.50 下仍然能召回 3 个 chunk，最高分分别约：

- D1：0.6922
- D2：0.6797

但模型最终回答了“资料中没有足够信息”。这说明：

```text
检索层认为它们和 RAG 文档相关；生成层根据上下文判断没有答案。
```

这是向量检索的典型问题：

```text
语义相近 ≠ 文档中有答案。
```

所以 threshold 只能做第一层粗过滤，不能承担“答案存在性判断”。

## 工程建议

### 推荐默认策略

```yaml
top-k: 3
threshold: 0.50
```

### 更稳的生产策略

不要只靠 threshold，应该增加二阶段判断：

1. 向量检索：`topK=3, threshold=0.50`
2. LLM 判断上下文是否足够回答
3. 如果不足，返回固定兜底：`资料中没有足够信息`

### 后续优化方向

1. 给接口返回 `hasEnoughContext` 或 `answerable`
2. 增加 rerank 层，先 top20 召回，再 rerank top3
3. 知识库外问题增加负样本评测集
4. 对 D1/D2 这类“主题相关但答案不存在”的问题，单独做 badcase 集合
5. 后续学习 Week4 Day2 混合检索时，用 BM25 验证是否能改善专有词/工程词误召回

## 一句话总结

当前系统的最佳默认阈值是 **0.50**。  
0.35 太宽，0.65 开始误伤，0.80 直接失明。  
但真正的坑是：**知识库外问题也能拿到 0.68+ 的相似度，所以 threshold 不能替代答案存在性判断。**
