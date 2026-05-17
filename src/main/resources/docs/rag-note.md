# RAG 学习笔记

RAG，全称 Retrieval-Augmented Generation，中文通常叫检索增强生成。它的核心思想不是让大模型凭空回答，而是在回答前先从外部知识库中检索相关资料，再把资料作为上下文交给大模型生成答案。

RAG 的核心流程包括五步：第一，加载原始文档；第二，将文档切分成多个 Chunk；第三，使用 Embedding 模型把 Chunk 转成向量；第四，将向量和原文一起存入向量数据库；第五，用户提问时，将问题也转成向量，再检索最相似的 Chunk，最后把检索结果拼进 Prompt，让大模型回答。

文档切分是 RAG 中非常关键的一步。Chunk 太小，会导致语义被切碎，模型拿不到完整上下文；Chunk 太大，会导致每个片段包含太多无关信息，检索时噪声变多。实际工程中通常会设置 overlap，也就是相邻 Chunk 之间保留一部分重叠内容，避免上下文断裂。

Embedding 模型负责把文本转换成向量。向量可以表达文本语义，语义相似的文本在向量空间中距离更近。中文 RAG 场景中，常用的模型包括 bge-large-zh、bge-m3 等。需要注意，同一个知识库从入库到查询必须使用同一个 Embedding 模型，不能混用。

向量数据库用于存储文本向量并进行相似度检索。常见选择包括 Chroma、Milvus、Qdrant、Elasticsearch。Java 后端场景中，Elasticsearch 的优势是生态成熟，容易和已有搜索系统集成。

检索策略会直接影响 RAG 效果。topK 表示返回最相似的前几个片段。topK 太小可能召回不足，topK 太大可能引入噪声。threshold 表示相似度阈值，可以过滤明显不相关的结果。

Hybrid Search 是一种混合检索策略，它结合关键词检索和向量检索。关键词检索擅长精确匹配术语、编号、专有名词；向量检索擅长语义相似问题。两者结合通常比单纯向量检索更稳。

Rerank 是检索后的重排序步骤。第一阶段可以召回较多候选片段，比如 top20；第二阶段用 reranker 模型重新判断问题和片段的相关性，筛选出最相关的 top3。Rerank 常用于提升精确率。

RAG 最常见的问题不是模型不够强，而是资料没召回、Chunk 切错、Embedding 模型不合适、检索参数设置不合理。如果检索结果本身就是错的，后面的 Prompt 写得再漂亮也没用。

## 混合检索实验语料

BM25 是一种经典的关键词检索算法。它根据查询词在文档中的出现次数、查询词的稀有程度、文档长度归一化来计算相关性分数。相比简单的 TF-IDF，BM25 会让词频增长逐渐饱和，避免某个词重复出现太多次就把分数无限拉高。

BM25 的核心优势是精确匹配。比如用户搜索 BM25、RRF、bge-reranker、Qwen3-Embedding、SKU-20260516、OrderService、NullPointerException 这类专有名词、英文缩写、代码标识符或者业务编号时，关键词检索通常比纯向量检索更稳。因为这些词本身就是关键信号，不能只依赖语义相似度。

向量检索的核心优势是语义匹配。比如用户问“怎么让知识库问答少答错一点”，即使文档中没有完全相同的句子，Embedding 也可能召回“提升 RAG 准确率”“优化检索链路”“减少幻觉”等相关片段。它适合处理同义表达、自然语言改写和模糊意图。

Hybrid Search 混合检索会同时执行向量检索和关键词检索，然后把两路结果融合。常见融合方法包括加权分数融合和 RRF。加权分数融合需要把 cosine 相似度和 BM25 分数归一化，否则两个分数尺度不同，调参会很别扭。RRF 只看排名，不关心原始分数，因此更适合作为入门实现。

RRF，全称 Reciprocal Rank Fusion，中文可以叫互倒数排名融合。它的公式是 score = 1 / (k + rank)，常用 k = 60。如果某个 chunk 在向量检索中排名第 2，在 BM25 中排名第 5，那么它的融合分数就是 1/(60+2) + 1/(60+5)。一个片段只要在多路检索中都靠前，融合后就会更容易排到前面。

一个典型的混合检索链路是：第一路用 Embedding 做 dense vector search，召回语义相似的 chunk；第二路用 BM25 做 sparse lexical search，召回关键词命中的 chunk；第三步用 RRF 合并排名；最后把融合后的 topK chunk 拼进 Prompt 交给大模型回答。

在企业知识库场景中，混合检索尤其重要。员工可能搜索“报销流程怎么走”，也可能搜索“FIN-2026-042 报销单状态码”。前者更适合向量检索，后者更适合关键词检索。如果系统只支持向量检索，编号、函数名、错误码、配置项、API 路径这类精确符号很容易被忽略。

在 Java 后端项目中，用户可能会搜索 OrderService、PaymentController、NullPointerException、IllegalArgumentException、spring.datasource.url、/api/orders/create 这类精确符号。BM25 对这些查询非常敏感，因为它直接匹配文本中的 token；向量检索可能理解这些词的大概含义，但不一定能稳定把包含精确符号的 chunk 排到第一。

本项目的实验目标是对比三种模式：vector 表示只使用向量检索；bm25 表示只使用关键词检索；hybrid 表示向量检索与 BM25 检索通过 RRF 融合。理想结果不是证明某一种永远最好，而是理解不同问题类型适合不同检索策略。

## Rerank 重排序详解

Rerank 是 RAG 检索链路中的精排步骤。向量检索和 BM25 都是粗筛：它们能快速从大量文档中召回大致相关的候选，但无法精确判断哪个候选最直接回答了用户问题。Rerank 的作用就是在粗筛结果上做二次精排，把最相关的推到前面。

粗筛和精排的区别可以用一个类比理解：向量检索就像相亲网站的标签匹配，量大速度快，但可能匹配到其实不合适的人；Rerank 就像面对面约会，慢但精确，能真正判断是否合拍。

### Bi-Encoder 和 Cross-Encoder

Embedding 模型使用的是 Bi-Encoder（双编码器）。Query 和 Document 分别独立编码，各走一遍模型，生成各自的向量，然后用余弦相似度计算相关性。这种方式速度快，因为 Document 向量可以预先计算并建索引，查询时只需编码 Query 再做向量搜索。但缺点是 Query 和 Document 在编码时互相不知道对方的存在，无法做深度语义交互。

Rerank 模型使用的是 Cross-Encoder（交叉编码器）。Query 和 Document 拼接在一起作为一个整体送入 Transformer，通过注意力机制在 Query 和 Document 之间做逐词交互，最终输出一个相关性分数。这种方式更精确，因为模型能同时看到问题和文档内容，判断它们是否真正匹配。但缺点是每个 Query-Doc 对都要单独跑一次模型，不能预建索引，所以只适合对少量候选做精排。

简单总结：Bi-Encoder 是分别看简历判断匹配度，快但粗；Cross-Encoder 是把两个人拉到一起面对面聊，慢但精。

### Rerank 在 RAG 中的位置

完整的 RAG 检索链路是：第一步用 Embedding 向量检索做粗筛，召回 Top-20 候选；第二步用 BM25 关键词检索做粗筛，召回 Top-20 候选；第三步用 Hybrid Search（RRF 融合）合并两路结果，得到合并后的 Top-20；第四步用 Rerank（Cross-Encoder）对这 20 个候选做精排，取 Top-3；最后把 Top-3 chunk 拼进 Prompt 交给大模型生成答案。

经验法则是粗筛阶段宁可多召回，不要漏掉好的候选。通常召回 10-20 条，然后 Rerank 精排取 3-5 条喂给 LLM。LLM 的 context window 有限，喂太多反而会降低回答质量。

### 主流 Rerank 模型

常用的 Rerank 模型包括 bge-reranker-v2-m3（BAAI出品，中文能力强，支持8192 tokens输入）、bge-reranker-large（较轻量，适合短文本）、cohere-rerank-v3（API调用简单，英文为主）、Jina-reranker-v2（多语言支持）。中文 RAG 场景首选 bge-reranker-v2-m3，它的 Cross-Encoder 基于 XLM-RoBERTa 架构，在中文语义匹配上表现优异。

### 性能权衡

Rerank 的单次耗时大约 50-200ms 每个 chunk，取决于 chunk 长度和模型大小。如果召回 20 条候选做 Rerank，总延迟增量约 500ms 到 2 秒，对于大多数应用场景是可接受的。成本方面，SiliconFlow 按请求次数计费，每次请求几毛钱。关键原则是 Rerank 的价值在于少而精，不是把所有文档都送进去重排，而是只对已经粗筛过的少量候选做精排。

## 检索策略对比总结

不同类型的问题适合不同检索策略。纯语义问题比如"怎么提升知识库问答准确率"更适合向量检索；精确匹配问题比如"BM25的k1参数默认值是多少"更适合 BM25；混合意图问题比如"Rerank 的 Cross-Encoder 和向量检索的 Embedding 有什么区别"适合 Hybrid Search 加 Rerank。

在实际项目中，检索策略的选择不是非此即彼。一个好的 RAG 系统通常默认使用 Hybrid Search + Rerank，这样无论是语义问题还是精确匹配问题都能得到较好的结果。只有在明确知道问题类型时，才切换到单一检索模式。

## Embedding 模型详细对比

中文场景常用的 Embedding 模型有 bge-large-zh（维度1024，中文专精，适合一般场景）、bge-m3（维度1024，多语言多功能，支持 dense+lexical+colbert 三种检索）、text-embedding-ada-002（OpenAI出品，维度1536，英文为主中文较弱）、Qwen3-Embedding（维度4096，最新模型，中文表现好但维度较大）。选择 Embedding 模型时需要考虑维度大小（维度越大存储成本越高）、中文能力（中文场景必须选中文强模型）、输入长度（长文档需要支持更长输入的模型）。

Embedding 模型的一个重要约束是：同一个知识库从入库到查询必须使用同一个模型，不能混用。不同模型的向量空间不同，用 bge-large-zh 入库的向量不能用 Qwen3-Embedding 的 Query 向量来检索，否则结果会完全错乱。

## 向量数据库深入选择

Chroma 是最简单的向量数据库，适合入门和本地实验，Python 生态友好但性能有限。Milvus 是高性能分布式向量数据库，适合大规模生产场景，支持亿级向量检索。Qdrant 是 Rust 写的高性能向量数据库，API 简洁，适合中等规模场景。Elasticsearch 从 8.x 开始原生支持向量检索，对 Java 后端项目最友好，可以同时做关键词检索和向量检索，天然支持 Hybrid Search。

选择向量数据库的关键考量是：数据规模（百万级以下用 Chroma/Qdrant 就够了）、查询并发（高并发选 Milvus 或 ES）、是否需要同时支持关键词检索（ES 最方便）、团队技术栈（Java 团队选 ES 或 Milvus SDK）。