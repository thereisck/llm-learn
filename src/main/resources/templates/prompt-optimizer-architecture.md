# Prompt优化器架构图

> 生成时间：2026-04-30
> 用途：Week 2 Day 7 实战项目设计

---

## 一、系统架构图

```mermaid
flowchart TD
    %% 用户交互层
    subgraph UI["用户交互层"]
        A[用户输入] --> B[任务描述]
        A --> C[Prompt方案列表]
        A --> D[测试参数配置]
    end

    %% 核心处理层
    subgraph CORE["核心处理层"]
        E[PromptTemplateManager<br/>模板管理器] --> F[模板加载]
        E --> G[模板渲染]
        G --> H[变量替换]
        
        I[LLMClient<br/>API客户端] --> J[并行调用]
        J --> K[响应收集]
        K --> L[Token统计]
        
        M[EvaluationEngine<br/>评估引擎] --> N[准确性评分]
        M --> O[完整度评分]
        M --> P[输出质量分析]
        
        Q[ComparisonReport<br/>报告生成器] --> R[对比表格]
        Q --> S[成本分析]
        Q --> T[推荐方案]
    end

    %% 数据存储层
    subgraph DATA["数据存储层"]
        U[(模板库<br/>Template DB)] --> E
        V[(测试结果<br/>Result Cache)] --> I
        W[(历史记录<br/>History DB)] --> Q
    end

    %% 外部服务
    subgraph EXT["外部服务"]
        X[OpenAI API] --> I
        Y[国内LLM API<br/>智谱/阿里] --> I
    end

    %% 数据流向
    B --> E
    C --> E
    D --> I
    
    H --> I
    L --> M
    P --> Q
    T --> Z[输出报告]
```

---

## 二、核心组件交互时序图

```mermaid
sequenceDiagram
    participant U as 用户
    participant PM as PromptTemplateManager
    participant LC as LLMClient
    participant EE as EvaluationEngine
    participant CR as ComparisonReport
    participant DB as 数据库
    participant API as LLM API

    %% 流程开始
    U->>PM: 提交任务 + Prompt方案列表
    PM->>DB: 加载模板
    DB-->>PM: 返回模板数据
    
    %% 模板渲染
    PM->>PM: 变量替换，生成最终Prompt
    
    %% 并行调用LLM
    PM->>LC: 发送渲染后的Prompt
    LC->>API: 并行调用（多个方案）
    API-->>LC: 返回响应 + Token消耗
    
    %% 结果评估
    LC->>EE: 提交响应结果
    EE->>EE: 准确性评分
    EE->>EE: 完整度评分
    EE->>EE: 质量分析
    
    %% 报告生成
    EE->>CR: 提交评估结果
    CR->>CR: 生成对比表格
    CR->>CR: 成本分析
    CR->>CR: 推荐最优方案
    
    %% 输出
    CR-->>U: 返回Markdown报告
    CR->>DB: 保存历史记录
```

---

## 三、模块职责脑图

```mermaid
mindmap
  root((Prompt优化器))
    用户交互
      任务输入
      Prompt方案选择
      参数配置
        temperature
        max_tokens
        model选择
    核心处理
      PromptTemplateManager
        模板加载
        变量替换
        版本管理
      LLMClient
        并行调用
        响应收集
        Token统计
        异步处理
      EvaluationEngine
        准确性评分
        完整度评分
        格式一致性
        人工评分接口
      ComparisonReport
        对比表格生成
        成本分析
        方案推荐
        Markdown输出
    数据存储
      模板库
        MySQL/PostgreSQL
        版本控制
      测试结果缓存
        Redis
        TTL设置
      历史记录
        持久化存储
        查询接口
    外部集成
      OpenAI API
      国内LLM API
        智谱GLM
        阿里Qwen
```

---

## 四、技术选型表

| 层级 | 组件 | 技术选型 | 原因 |
|------|------|----------|------|
| **Web层** | REST API | Spring Boot | Java后端标准 |
| **异步处理** | 并行调用 | CompletableFuture | 原生支持、易控制 |
| **LLM SDK** | API调用 | OpenAI Java SDK | 官方支持、类型安全 |
| **缓存** | 结果缓存 | Redis | 快、支持TTL |
| **存储** | 模板库 | MySQL/PostgreSQL | 成熟、查询方便 |
| **报告输出** | Markdown | CommonMark | Java Markdown库 |

---

## 五、核心接口设计

```java
// PromptTemplateManager 接口
public interface PromptTemplateManager {
    PromptTemplate loadTemplate(String templateId);
    String renderTemplate(String templateId, Map<String, String> variables);
    void registerTemplate(PromptTemplate template);
    List<PromptTemplate> listTemplates(String category);
}

// LLMClient 接口
public interface LLMClient {
    List<LLMResponse> batchCall(List<String> prompts, LLMConfig config);
    CompletableFuture<LLMResponse> asyncCall(String prompt, LLMConfig config);
    TokenUsage calculateTokens(String prompt);
}

// EvaluationEngine 接口
public interface EvaluationEngine {
    EvaluationResult evaluate(String response, String expectedOutput);
    List<EvaluationResult> batchEvaluate(List<String> responses, String expectedOutput);
    QualityScore analyzeQuality(String response);
}

// ComparisonReport 接口
public interface ComparisonReport {
    String generateMarkdown(List<EvaluationResult> results);
    CostAnalysis analyzeCost(List<TokenUsage> usages);
    Recommendation recommendBest(List<EvaluationResult> results);
}
```

---

## 六、数据流向总结

```
输入：任务描述 + Prompt方案列表 + 测试参数
  ↓
模板管理：加载模板 → 渲染（变量替换）→ 生成最终Prompt
  ↓
API调用：并行调用LLM → 收集响应 → 统计Token消耗
  ↓
效果评估：准确性评分 → 完整度评分 → 输出质量分析
  ↓
报告生成：对比表格 → 成本分析 → 推荐最优方案
  ↓
输出：Markdown格式报告（含对比、成本、推荐）
```

---

**更新记录**：
- 2026-04-30：初始版本（架构图设计）