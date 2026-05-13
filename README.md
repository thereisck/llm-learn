# llm-learn

`llm-learn` 是一个基于 Spring Boot 的大模型学习与实验项目，当前包含流式对话、代码注释生成、RAG 问答、Prompt 模板管理与 Prompt A/B 测试等能力。

## 功能概览

- **流式对话**：提供兼容 Chat Completions 风格的 SSE 接口。
- **代码注释**：根据代码片段和语言类型生成说明或注释。
- **RAG 问答**：基于本地文档切分、向量检索和大模型生成答案。
- **Prompt 优化**：支持模板注册、渲染、测试、A/B 对比和报告输出占位。
- **本地持久化**：使用 SQLite 与 Spring Data JPA 存储 Prompt 模板等数据。

## 技术栈

- Java 17
- Spring Boot 4.0.5
- Maven Wrapper
- Web MVC / WebFlux
- Spring Data JPA + SQLite
- LangChain4j、OpenAI Java SDK
- Lombok、Fastjson2、Pebble、SnakeYAML
- JUnit 5

## 项目结构

```text
src/main/java/com/ck/custom/llmlearn
├── LlmLearnApplication.java          # 应用启动类
├── config/                           # Spring 配置
├── contoller/                        # HTTP 控制器（保持当前包名拼写）
├── domain/                           # 请求、响应和领域模型
├── prompt/                           # Prompt 模板引擎示例
├── prompt_optimizer/                 # Prompt 优化相关功能
├── service/                          # 服务接口
├── service/impl/                     # 服务实现
└── utils/                            # 工具类

src/main/resources
├── application.properties            # 应用配置
├── docs/                             # RAG 示例文档
├── static/                           # 静态资源
└── templates/                        # Prompt 模板和示例文件
```

## 环境要求

- JDK 17+
- Maven 可通过仓库内 `./mvnw` 使用，无需单独安装 Maven
- 如需调用真实大模型服务，请准备兼容 OpenAI API 的服务地址、模型名称和 API Key

## 快速开始

```bash
# 编译项目
./mvnw clean compile

# 运行测试
./mvnw test

# 本地启动，默认端口 8900
./mvnw spring-boot:run

# 构建可执行 JAR
./mvnw clean package
```

启动成功后访问：`http://localhost:8900`。

## 配置说明

主要配置位于 `src/main/resources/application.properties`：

- `server.port`：服务端口，默认 `8900`。
- `openai.api.*`：对话和代码注释相关模型配置。
- `llm.*`：RAG 与 Prompt 优化使用的大模型配置。
- `spring.datasource.url`：SQLite 数据库路径，默认 `data/llm_learn.db`。
- `rag.*`：RAG 文档切分、召回数量和相似度阈值配置。

请不要提交真实 API Key。建议使用环境变量或本地覆盖配置管理敏感信息。

## 常用接口示例

### 流式对话

```bash
curl -N -X POST http://localhost:8900/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "Pro/zai-org/GLM-5.1",
    "stream": true,
    "messages": [
      {"role": "user", "content": "你好，介绍一下这个项目"}
    ]
  }'
```

### 代码注释

```bash
curl -X POST http://localhost:8900/api/annotate \
  -H 'Content-Type: application/json' \
  -d '{
    "language": "java",
    "code": "public int add(int a, int b) { return a + b; }"
  }'
```

### RAG 问答

```bash
curl -X POST http://localhost:8900/rag/query \
  -H 'Content-Type: application/json' \
  -d '{
    "question": "RAG 的核心流程是什么？",
    "threshold": 0.6
  }'
```

### Prompt 模板管理

```bash
# 注册模板
curl -X POST http://localhost:8900/api/prompt/templates \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "translate-demo",
    "name": "翻译模板",
    "category": "translation",
    "template": "请将以下内容翻译成中文：${text}",
    "variables": {"text": "待翻译文本"}
  }'

# 渲染模板
curl -X POST http://localhost:8900/api/prompt/render \
  -H 'Content-Type: application/json' \
  -d '{
    "templateId": "translate-demo",
    "params": {"text": "Hello world"}
  }'
```

## 测试

测试代码位于 `src/test/java`，命名建议使用 `*Tests` 后缀。提交前请执行：

```bash
./mvnw test
```

## 开发约定

- 包名统一位于 `com.ck.custom.llmlearn` 下。
- Controller、Service、Domain/DTO 分层保持清晰。
- Java 代码使用 4 空格缩进，类名使用 `PascalCase`，方法和字段使用 `camelCase`。
- 构建产物位于 `target/`，不要手动修改或提交。

## 相关文档

- `AGENTS.md`：仓库贡献与协作指南。
- `PROMPT_OPTIMIZER_README.md`：Prompt 优化模块补充说明。
- `MAVEN-QUICK-GUIDE.txt`：Maven 环境配置参考。
