# 星云科技代码开发规范

## 一、Git 规范

### 1.1 分支策略
采用 Git Flow 模型：

| 分支 | 用途 | 命名规则 | 生命周期 |
|------|------|----------|----------|
| main | 生产分支 | main | 永久 |
| develop | 开发分支 | develop | 永久 |
| feature | 功能开发 | feature/JIRA编号-简述 | 合并后删除 |
| hotfix | 紧急修复 | hotfix/JIRA编号-简述 | 合并后删除 |
| release | 发布准备 | release/v版本号 | 合并后删除 |

### 1.2 Commit Message 格式
采用 Conventional Commits 规范：

```
<type>(<scope>): <subject>

<body>

<footer>
```

**type 枚举**：
- feat：新功能
- fix：修复Bug
- refactor：重构（不改变功能）
- perf：性能优化
- docs：文档变更
- test：测试相关
- ci：CI/CD变更
- chore：构建/工具变更

**示例**：
```
feat(rag): 新增多路召回检索模式

- 支持 vector + BM25 + exactMatch 三路并行召回
- 新增 EnterpriseRagConfig 配置类
- 完成对比实验，multi_route 在小知识库场景增益有限

Refs: #NOVA-1234
```

### 1.3 代码审查
- 所有代码合并到 develop/main 必须经过 Code Review
- Review 最低人数：2人（含1名P7+）
- Review 超时：48小时未审自动提醒，72小时未审由Leader介入
- 自我 Review：提交MR前先自行检查diff，减少无效Review轮次

## 二、Java 编码规范

### 2.1 基本规则
- 遵循阿里巴巴 Java 开发手册（黄山版）
- 使用 Lombok 减少样板代码（@Data、@Builder、@Slf4j）
- 异常处理：禁止空 catch，禁止异常只打印日志不处理
- 日志规范：使用 SLF4J，禁止 System.out.println

### 2.2 命名规范
- 类名：UpperCamelCase（RagService、SemanticChunker）
- 方法名：lowerCamelCase（queryByHybrid、buildSearchPipeline）
- 常量：UPPER_SNAKE_CASE（MAX_RETRY_COUNT、DEFAULT_THRESHOLD）
- 包名：全小写（com.starcloud.novarag.service.rag）

### 2.3 注释规范
- 类注释：说明类职责，使用 Javadoc 格式
- 公共方法注释：说明参数含义、返回值、异常情况
- 复杂逻辑注释：解释"为什么"而非"是什么"
- 禁止无意义注释：如 `// 设置名称` 旁边就是 `setName(name)`

## 三、API 设计规范

### 3.1 RESTful 规范
- URL 使用小写+中划线：`/api/v1/rag/query`
- 使用复数名词表示资源集合：`/api/v1/documents`
- 版本号放在 URL 中：`/api/v1/` 而非 Header
- 使用标准 HTTP 方法：GET查询、POST创建、PUT更新、DELETE删除

### 3.2 响应格式
统一响应结构：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "timestamp": 1620000000000
}
```

错误响应：

```json
{
  "code": 4001,
  "message": "文档格式不支持",
  "data": null,
  "timestamp": 1620000000000
}
```

### 3.3 错误码规范
| 范围 | 模块 |
|------|------|
| 4000-4099 | 文档解析模块 |
| 4100-4199 | 检索模块 |
| 4200-4299 | Rerank模块 |
| 4300-4399 | 生成模块 |
| 5000-5099 | 系统级错误 |

## 四、测试规范

### 4.1 单元测试
- 覆盖率要求：核心业务逻辑 ≥ 80%
- 测试命名：`test_{方法名}_{场景}_{期望结果}`
- 使用 Mockito 模拟外部依赖（LLM API、向量库）
- 禁止测试之间有依赖关系

### 4.2 集成测试
- 使用 Spring Boot Test + TestContainers（MySQL、Redis）
- 集成测试标注 `@IntegrationTest`，CI 流水线单独运行
- 集成测试不应依赖外部服务（用 Mock 替代 SiliconFlow API）

## 五、CI/CD 规范

### 5.1 流水线阶段
1. Lint Check（代码风格检查）
2. Unit Test（单元测试）
3. Integration Test（集成测试）
4. Build Package（打包）
5. Deploy Staging（部署到预发布环境）
6. Smoke Test（冒烟测试）
7. Deploy Prod（生产部署，需人工审批）

### 5.2 发布规范
- 发布窗口：周二/周四 10:00-12:00
- 发布负责人：至少1名P7+在场
- 发布失败回滚：5分钟内决策，10分钟内完成回滚