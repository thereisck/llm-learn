# Rerank 重排序模型 — 原理教义

> Week 4 Day 3 | RAG系统进阶 | 2026-05-17

---

## 一、为什么需要 Rerank？

你已经有了一条完整的 RAG 检索链路：

```
用户问题 → Embedding向量检索（或 BM25 / Hybrid）→ Top-K chunk → 喂给 LLM → 生成回答
```

但问题是：**向量检索是"粗筛"，不是"精排"。**

举个真实场景：
- 用户问："BM25 的词频饱和参数 k1 怎么调？"
- 向量检索召回 Top-10，排第1的是一篇介绍 BM25 基本概念的大段文字
- 但真正回答"k1怎么调"的那段，排在第7

向量检索用余弦相似度做匹配，它只能判断"这两段文字大致在聊同一件事"，但**无法精确判断哪段文字最直接回答了你的问题**。

Rerank 就是干这个活的——**在粗筛结果上做精排，把最相关的推到前面。**

---

## 二、双编码器 vs 交叉编码器（核心原理）

这是理解 Rerank 最关键的概念对比。

### 2.1 双编码器（Bi-Encoder）—— 你已经用过的 Embedding

```
Query: "BM25的k1怎么调"
  → Encoder → 向量 q [0.23, -0.45, 0.67, ...]

Doc Chunk: "BM25是一种基于TF-IDF的检索算法..."
  → Encoder → 向量 d [0.21, -0.43, 0.65, ...]

相关性 = cosine(q, d) = 0.92
```

**关键特点**：
- Query 和 Doc **分别独立编码**，各走一遍模型
- 编码后只需算一次余弦相似度，速度极快
- 但缺点：**两个向量在编码时互相不知道对方的存在**，无法做深度语义交互

打个比方：就像两个人各自写了一篇自我介绍，你通过对比两篇介绍的"关键词重叠度"来判断他们是否匹配——快，但粗。

### 2.2 交叉编码器（Cross-Encoder）—— Rerank 用的就是这个

```
输入拼接: "[Query] BM25的k1怎么调 [SEP] [Doc] BM25是一种基于TF-IDF的检索算法..."
  → 整段送入 Transformer → 输出一个相关性分数（0.03）

输入拼接: "[Query] BM25的k1怎么调 [SEP] [Doc] k1参数控制词频饱和度，默认1.2，范围0.5-2.0..."
  → 整段送入 Transformer → 输出一个相关性分数（0.91）
```

**关键特点**：
- Query 和 Doc **拼在一起**，作为一个整体送入模型
- Transformer 的注意力机制可以在 Query 和 Doc 之间做**逐词交互**
- 输出是一个**实数分数**（不是向量），直接表示相关性

打个比方：就像把两个人拉到同一个房间里，让他们面对面聊30分钟，然后你判断他们是否真的合拍——慢，但精确。

### 2.3 对比总结

| 维度 | Bi-Encoder（Embedding） | Cross-Encoder（Rerank） |
|------|------------------------|------------------------|
| 输入方式 | Query、Doc 分别编码 | Query+Doc 拼接后一起编码 |
| 交互深度 | 无交互（只算向量距离） | 深度交互（Transformer逐词注意力） |
| 输出 | 向量（用于索引和检索） | 单个相关性分数（用于排序） |
| 速度 | 极快（一次编码，多次检索） | 较慢（每个Query-Doc对都要跑一次模型） |
| 用途 | 粗筛：从百万文档中召回 Top-K | 精排：从 Top-K 中选出 Top-N |
| 能建索引 | ✅ 可以预计算向量索引 | ❌ 不能建索引，必须实时计算 |

**这就是为什么 Rerank 不能替代向量检索**——它太慢了，不能对百万文档逐个打分。但它可以对 10~50 条召回结果做精排，因为计算量可控。

---

## 三、Rerank 在 RAG 中的位置

```
完整 RAG 检索链路（加了 Rerank）：

用户问题
  ↓
Embedding向量检索（粗筛） → Top-20
  ↓
BM25关键词检索（粗筛） → Top-20
  ↓
Hybrid Search 融合（RRF） → 合并 Top-20
  ↓
Rerank 精排（Cross-Encoder） → Top-3
  ↓
喂给 LLM 生成回答
```

**经验法则**：
- 粗筛召回 Top-10~20（宁可多召回，不漏）
- Rerank 精排取 Top-3~5（精确就够了）
- LLM 的 context window 有限，喂太多反而降低回答质量

---

## 四、主流 Rerank 模型对比

| 模型 | 中文能力 | 输入长度 | 调用方式 | 推荐场景 |
|------|---------|---------|---------|---------|
| **BAAI/bge-reranker-v2-m3** | ⭐⭐⭐⭐⭐ | 8192 tokens | SiliconFlow API / 本地部署 | 中文RAG首选 |
| **BAAI/bge-reranker-large** | ⭐⭐⭐⭐ | 512 tokens | 本地部署 | 短文本场景 |
| **BAAI/bge-reranker-v2.5-gemma2-lightweight** | ⭐⭐⭐⭐⭐ | 8192 tokens | 本地部署 | 资源受限环境 |
| **Cohere/rerank-v3** | ⭐⭐⭐ | 无明确限制 | API调用 | 英文为主 + 快速接入 |
| **Jina-reranker-v2-base-multilingual** | ⭐⭐⭐⭐ | 8192 tokens | API/本地 | 多语言场景 |

**我们的选择**：`BAAI/bge-reranker-v2-m3`
- 中文能力强（你做的是中文RAG）
- SiliconFlow 有 API，不用本地部署
- 支持 8192 tokens，覆盖长 chunk

---

## 五、SiliconFlow Rerank API 调用方式

SiliconFlow 提供了 bge-reranker-v2-m3 的 API，直接调用即可：

```bash
curl -X POST https://api.siliconflow.cn/v1/rerank \
  -H "Authorization: Bearer $SILICONFLOW_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "BAAI/bge-reranker-v2-m3",
    "query": "BM25的k1参数怎么调",
    "documents": [
      "BM25是一种基于TF-IDF改进的检索算法，广泛用于信息检索系统",
      "k1参数控制词频饱和度，默认值1.2，建议范围0.5-2.0，数值越大对高频词越敏感",
      "b参数控制文档长度归一化，默认0.75，设为0则完全忽略文档长度",
      "BM25的计算公式为：score(D,Q) = Σ IDF(qi) · (f(qi,D)·(k1+1)) / (f(qi,D)+k1·(1-b+b·|D|/avgdl))"
    ],
    "return_documents": true,
    "top_n": 3
  }'
```

**返回结构**：
```json
{
  "id": "rerank-xxx",
  "results": [
    {
      "index": 1,
      "relevance_score": 0.91,
      "document": { "text": "k1参数控制词频饱和度..." }
    },
    {
      "index": 3,
      "relevance_score": 0.78,
      "document": { "text": "BM25的计算公式为..." }
    },
    {
      "index": 0,
      "relevance_score": 0.03,
      "document": { "text": "BM25是一种基于TF-IDF改进..." }
    }
  ]
}
```

注意看：原来排第0位的概念介绍，Rerank 后 relevance_score 只有 0.03，掉到了最后；而真正回答问题的第1位，分数 0.91，稳稳排第一。

---

## 六、从原理到直觉——三句话总结

1. **Embedding 是相亲网站的"标签匹配"**：快，量大，但可能匹配到你其实不喜欢的人
2. **Rerank 是"面对面约会"**：慢，但能真正判断是否合拍
3. **RAG 的最佳实践**：先用 Embedding/BM25/Hybrid 从百万文档中召回 Top-20（相亲），再用 Rerank 精排取 Top-3（约会），最后喂给 LLM（结婚）

---

## 七、性能权衡（实际项目必须考虑）

| 因素 | 数值参考 | 说明 |
|------|---------|------|
| Rerank 单次耗时 | ~50-200ms/chunk | 取决于 chunk 长度和模型大小 |
| 推荐召回数量 | Top-10~20 | 太多会增加 Rerank 总耗时 |
| 推荐 Rerank 输出 | Top-3~5 | 给 LLM 的最佳数量 |
| 总延迟增量 | ~500ms-2s | 20条×100ms ≈ 2s，可接受 |
| 成本 | SiliconFlow 按次计费 | 每次请求几毛钱 |

**关键原则**：Rerank 的价值在于"少而精"——不是把100条全送进去重排，而是把已经粗筛过的10-20条做精排。

---

## 八、今天实验目标

在 llm-learn 项目中：

1. **接入 SiliconFlow Rerank API**（在 RagService 中新增 rerank 步骤）
2. **对比实验**：
    - 无 Rerank：Hybrid Search 直接取 Top-3 → LLM
    - 有 Rerank：Hybrid Search 取 Top-20 → Rerank → Top-3 → LLM
3. **观察指标**：
    - 回答质量（是否更直接、更精确）
    - Top-3 chunk 内容对比（Rerank 前后，哪些 chunk 被替换了）
    - 耗时增量

---

*教义结束。开始敲代码吧。*