# NovaRAG 企业级知识库系统技术文档

## 系统概述

NovaRAG 是星云科技自主研发的企业级知识库问答系统，基于 RAG（Retrieval-Augmented Generation）架构，支持多模态文档检索与智能问答。

## 架构设计

### 核心模块

NovaRAG 由以下5个核心模块构成：

1. **DocParser** - 文档解析引擎
   - 支持 PDF、Word、Markdown、TXT 四种格式
   - 自动提取文档元数据（标题、作者、创建时间）
   - OCR 模块支持图片内文字识别（基于 PaddleOCR）

2. **ChunkEngine** - 文档切分引擎
   - FixedChunker：固定大小切分，chunk_size=512，overlap=64
   - RecursiveChunker：基于段落+句子递归切分
   - SemanticChunker：基于 Embedding 相似度断点检测，支持三种策略（THRESHOLD / DIFF / PERCENTILE）
   - 默认使用 SemanticChunker（THRESHOLD=0.5）

3. **SearchEngine** - 检索引擎
   - VectorSearcher：基于 Chroma 向量数据库，使用 bge-large-zh-v1.5 Embedding
   - Bm25Searcher：基于 BM25 算法的关键词检索
   - HybridSearcher：向量+BM25 混合检索，使用 RRF 融合策略，k=60
   - 支持 exactMatchBoost：精确关键词匹配加权

4. **RerankEngine** - 重排序引擎
   - 使用 bge-reranker-v2-m3 Cross-Encoder 模型
   - 输入：候选文档列表（topK=10）
   - 输出：精排后 topN=5 文档列表

5. **Generator** - 生成引擎
   - 默认使用 GPT-4o-mini 模型
   - 支持 SiliconFlow GLM-4-Plus 作为国内备用模型
   - System Prompt 模板可根据业务域定制

### API 接口

#### 文档上传接口
```
POST /api/v1/documents/upload
Content-Type: multipart/form-data

参数：
- file: 文档文件（PDF/Word/MD/TXT）
- domain: 业务域标识（hr-policy / tech-doc / product-manual）
- chunkStrategy: 切分策略（fixed / recursive / semantic）
- overwrite: 是否覆盖已有文档（boolean）

返回：
{
  "docId": "doc_20260521_001",
  "chunkCount": 23,
  "status": "indexed"
}
```

#### 问答接口
```
POST /api/v1/rag/query
Content-Type: application/json

参数：
{
  "question": "入职第一年有多少天年假？",
  "searchMode": "hybrid_rerank",
  "topK": 5,
  "threshold": 0.5,
  "domain": "hr-policy",
  "enableCompression": true
}

返回：
{
  "answer": "根据公司考勤制度，入职满1年的员工享有5天年假。",
  "sources": [
    {"docId": "doc_001", "chunkIndex": 3, "score": 0.92, "content": "入职满1年：5天年假"}
  ],
  "confidence": 0.92,
  "outOfDomain": false
}
```

#### 批量问答接口
```
POST /api/v1/rag/batch-query
Content-Type: application/json

参数：
{
  "questions": ["问题1", "问题2", ...],
  "searchMode": "hybrid_rerank",
  "topK": 5,
  "threshold": 0.5
}

返回：数组形式，每个元素同单条问答接口返回格式
```

## 部署配置

### 环境要求
- Java 17+
- Spring Boot 3.2+
- Chroma 0.4.18+（Docker部署）
- Python 3.10+（Rerank服务）
- 内存：至少 4GB JVM 堆内存

### 关键配置项
```yaml
novarag:
  embedding:
    model: bge-large-zh-v1.5
    dimension: 1024
    provider: siliconflow
  search:
    defaultMode: hybrid_rerank
    defaultTopK: 5
    defaultThreshold: 0.5
    rrfK: 60
  rerank:
    model: bge-reranker-v2-m3
    provider: siliconflow
    topN: 5
  generator:
    defaultModel: gpt-4o-mini
    maxTokens: 2048
    temperature: 0.3
  outOfDomain:
    enabled: true
    threshold: 0.4
    rejectMessage: "该问题不在当前知识库范围内，请咨询相关部门获取准确信息。"
```