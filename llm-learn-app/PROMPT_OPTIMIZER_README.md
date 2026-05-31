# Prompt优化系统完整实现文档

## 项目概述

**项目路径**: `/Users/zhiweizhang/Downloads/aicc/workspace/llm-learn/src/main/java/com/ck/custom/llmlearn/prompt_optimizer/`

**前端路径**: `/Users/zhiweizhang/Downloads/aicc/workspace/llm-learn/prompt_optimizer_frontend/`

---

## 已解决的问题

### 1. ✅ 阿里云Qwen调用实现

**文件**: `client/QwenLLMClient.java`

**核心改进**:
- HTTP调用阿里云dashscope API
- 正确处理返回的 `usage` 字段（prompt_tokens + completion_tokens）
- 支持配置温度、最大Token等参数

**使用方法**:
```java
LLMClient client = new QwenLLMClient();
LLMResponse response = client.call("你好", LLMConfig.defaultConfig());
TokenUsage usage = response.getTokenUsage(); // 自动获取API返回的真实Token消耗
```

**环境变量配置**:
```bash
export DASHSCOPE_API_KEY=your_api_key_here
```

---

### 2. ✅ LLM-as-a-Judge评估引擎

**文件**: `engine/LLMEvaluationEngine.java`

**核心改进**:
- 用Qwen模型评估输出质量（取代关键词匹配）
- 5维度评分：准确性、流畅性、专业度、完整度、格式规范
- 自动生成改进建议

**评估流程**:
```
1. 构建评估Prompt（让LLM扮演"评估专家"）
2. 调用Qwen进行评估
3. 解析JSON格式评估结果
4. 降级策略：LLM失败时降级为简单评估
```

---

### 3. ✅ 整体串联实现

**文件**:
- `service/PromptOptimizerService.java` - 核心服务
- `controller/PromptOptimizerController.java` - REST API

**完整流程**:
```
模板渲染 → 多模型调用 → LLM评估 → 报告生成 → 推荐最优
```

---

### 4. ✅ A/B测试支持

**Service方法**: `abTest(ABTestRequest request)`

**流程**:
```
1. 并行调用多个Prompt方案
2. 并行LLM评估
3. 生成对比报告（成本、评分、推荐）
4. 返回最优方案
```

---

### 5. ✅ 前端可视化界面

**技术栈**: React + Ant Design + Vite

**功能页面**:
- `/templates` - 模板管理（增删改查）
- `/test` - Prompt测试（单次测试 + 报告）
- `/abtest` - A/B对比（多方案对比 + 推荐）

---

## API接口文档

### 模板管理

#### 注册模板
```
POST /api/prompt/templates
Body: {
  "name": "翻译模板",
  "category": "translation",
  "template": "请将以下内容翻译成${language}：\n${text}"
}
```

#### 获取模板列表
```
GET /api/prompt/templates?category=translation
```

#### 渲染模板
```
POST /api/prompt/render
Body: {
  "templateId": "template-001",
  "params": {
    "language": "中文",
    "text": "Hello World"
  }
}
```

---

### Prompt测试

#### 单次测试
```
POST /api/prompt/test
Body: {
  "prompt": "请解释什么是Prompt工程",
  "expectedOutput": "Prompt工程是...",
  "config": {
    "model": "qwen-max",
    "temperature": 0.7,
    "maxTokens": 2048
  }
}

Response: {
  "prompt": "...",
  "response": "LLM输出内容",
  "tokenUsage": {
    "inputTokens": 100,
    "outputTokens": 200,
    "totalTokens": 300
  },
  "latencyMs": 1500,
  "evaluation": {
    "accuracyScore": 85,
    "completenessScore": 80,
    "overallScore": 82,
    "grade": "良好(B)",
    "recommendation": "质量合格，建议优化细节"
  },
  "report": "Markdown报告"
}
```

---

### A/B测试

#### 对比测试
```
POST /api/prompt/abtest
Body: {
  "prompts": [
    { "prompt": "方案1的Prompt..." },
    { "prompt": "方案2的Prompt..." }
  ],
  "expectedOutput": "期望输出...",
  "config": { "model": "qwen-max" }
}

Response: {
  "details": [
    { "index": 1, "response": "...", "evaluation": {...} },
    { "index": 2, "response": "...", "evaluation": {...} }
  ],
  "comparisonReport": "Markdown对比报告",
  "recommendation": {
    "bestIndex": 0,
    "bestScore": 85,
    "reason": "综合评分更高"
  },
  "costAnalysis": {
    "totalTokens": 600,
    "totalCostUSD": 0.0015
  }
}
```

---

## 启动指南

### 1. 后端启动

```bash
cd /Users/zhiweizhang/Downloads/aicc/workspace/llm-learn

# 配置环境变量
export DASHSCOPE_API_KEY=your_api_key

# 启动Spring Boot
mvn spring-boot:run
```

**访问**: http://localhost:8080

---

### 2. 前端启动

```bash
cd /Users/zhiweizhang/Downloads/aicc/workspace/llm-learn/prompt_optimizer_frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

**访问**: http://localhost:3000

---

## 项目结构

### 后端结构
```
prompt_optimizer/
├── client/
│   ├── LLMClient.java              ← 接口
│   ├── QwenLLMClient.java          ← 阿里云实现 ✅
│   ├── MockLLMClient.java          ← Mock实现
│   ├── LLMConfig.java              ← 配置
│   ├── LLMResponse.java            ← 响应
│   └── TokenUsage.java             ← Token统计
│
├── engine/
│   ├── EvaluationEngine.java       ← 接口
│   ├── LLMEvaluationEngine.java    ← LLM评估 ✅
│   ├── SimpleEvaluationEngine.java ← 降级方案
│   ├── EvaluationResult.java       ← 评估结果
│   ├── QualityScore.java           ← 质量评分
│   └── ComparisonAnalysis.java     ← 对比分析
│
├── service/
│   └── PromptOptimizerService.java ← 核心服务 ✅
│
├── controller/
│   └── PromptOptimizerController.java ← REST API ✅
│
├── manager/
│   ├── PromptTemplateManager.java  ← 模板管理接口
│   └── InMemoryPromptTemplateManager.java ← 实现
│
├── report/
│   ├── ComparisonReport.java       ← 报告接口
│   ├── MarkdownComparisonReport.java ← Markdown实现
│   ├── CostAnalysis.java           ← 成本分析
│   └── Recommendation.java         ← 推荐
│
└── model/
    ├── PromptTemplateDTO.java      ← 模板DTO
    ├── TestRequest.java            ← 测试请求 ✅
    ├── TestResult.java             ← 测试结果 ✅
    ├── ABTestRequest.java          ← A/B请求 ✅
    ├── ABTestResult.java           ← A/B结果 ✅
    └── RenderRequest.java          ← 渲染请求 ✅
```

### 前端结构
```
prompt_optimizer_frontend/
├── src/
│   ├── App.jsx                     ← 主应用
│   ├── main.jsx                    ← 入口
│   ├── components/
│   │   └── Layout.jsx              ← 布局组件
│   └── pages/
│   │   ├── TemplateManage.jsx      ← 模板管理 ✅
│   │   ├── PromptTest.jsx          ← Prompt测试 ✅
│   │   └── ABTest.jsx              ← A/B对比 ✅
│
├── package.json                    ← 依赖配置
├── vite.config.js                  ← Vite配置
└── index.html                      ← HTML入口
```

---

## 核心特性总结

| 特性 | 实现方式 | 文件 |
|------|----------|------|
| **阿里云调用** | HTTP调用 + usage解析 | QwenLLMClient.java |
| **LLM评估** | Prompt构建 + JSON解析 | LLMEvaluationEngine.java |
| **整体串联** | Service整合 + REST API | PromptOptimizerService.java |
| **A/B测试** | 并行调用 + 对比报告 | PromptOptimizerService.abTest() |
| **前端界面** | React + Ant Design | 3个页面组件 |

---

## 待完善功能

1. **模板持久化** - 当前使用内存存储，需改为数据库
2. **报告存储** - 报告查询API待实现
3. **历史记录** - 测试历史追溯
4. **用户系统** - 多用户隔离

---

**创建时间**: 2026-04-30
**作者**: Kernel（空少的技术助手）