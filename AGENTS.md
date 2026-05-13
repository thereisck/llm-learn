# 仓库指南

## 项目结构与模块组织
- Java 源码位于 `src/main/java/com/ck/custom/llmlearn`。
- HTTP 入口位于 `contoller/`（保持当前包名拼写），服务接口位于 `service/`，实现类位于 `service/impl/`，共享 DTO/领域模型位于 `domain/`，Spring 配置位于 `config/`。
- 应用启动类是 `LlmLearnApplication.java`。
- 运行时配置位于 `src/main/resources/application.properties`；本地默认端口为 `8900`。
- 测试代码位于 `src/test/java/...`，并应镜像主代码包路径。当前基础测试类是 `LlmLearnApplicationTests`。
- 构建产物生成在 `target/`，不要手动编辑该目录内容。

## 构建、测试与开发命令
- `./mvnw clean compile` — 清理并编译 Java 17 项目。
- `./mvnw test` — 通过 Maven Surefire 运行单元测试和集成测试。
- `./mvnw spring-boot:run` — 在 `http://localhost:8900` 启动 Spring Boot 应用。
- `./mvnw clean package` — 在 `target/` 下构建可执行 JAR。
- `./maven-env-config.sh` 和 `./apply-maven-config.sh` — 用于 Maven 设置的可选本地辅助脚本。

## 编码风格与命名规范
- 使用 Java 17、UTF-8 文件编码和 4 空格缩进。
- 遵循现有 Spring 分层：Controller → Service → Domain/DTO。
- 类名使用 `PascalCase`，方法和字段使用 `camelCase`，常量使用 `UPPER_SNAKE_CASE`。
- 包名保持小写，并放在 `com.ck.custom.llmlearn` 下。
- 已使用 Lombok 的地方可继续沿用，但公共 API 和服务边界应保持清晰显式。

## 测试指南
- 使用 Spring Boot test starter 提供的 JUnit 测试栈。
- 测试类放在 `src/test/java` 下，并与源码包路径保持镜像。
- 测试类命名以 `*Tests` 结尾，例如 `ChatControllerTests`。
- 优先覆盖 Controller 和 Service 行为，同时包含成功路径和失败路径。
- 提交 Pull Request 前运行 `./mvnw test`。

## 提交与 Pull Request 指南
- 当前没有可参考的项目 Git 历史；后续请使用 Conventional Commits，例如 `feat: add stream chat endpoint` 或 `fix: handle null message list`。
- 每个提交聚焦一个逻辑变更。
- Pull Request 应包含目的、关键变更、测试证明以及配置更新说明。
- 关联相关 issue 或任务；涉及 API 变更时，提供示例请求和响应载荷。

## 安全与配置提示
- 不要提交 API Key、Token 或其他敏感信息。
- `application.properties` 应保持非敏感；凭据请使用环境变量或仅本地生效的覆盖配置。
- 在 Pull Request 描述中说明所需外部凭据或运行时前提。
