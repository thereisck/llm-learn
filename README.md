# LLM Learn — 大模型应用开发实战项目

> Java后端程序员转型大模型应用开发的完整学习路径——从RAG到Agent，从理论到代码。

## 📁 项目结构

```
llm-learn/
├── llm-learn-app/                    # Spring Boot主项目
│   ├── src/main/java/                # Java源码（RAG/Embedding/向量检索等）
│   ├── src/main/python/              # Python源码
│   │   ├── agent/                    # Agent模块（MCP/工具调用/安全Agent等）
│   │   ├── mini_harness/             # ⭐ 迷你版Agent Harness（核心项目）
│   │   ├── react_minimal.py          # ReAct模式最小实现
│   │   ├── react_with_real_llm.py    # ReAct+真实LLM
│   │   ├── function_calling_minimal.py # Function Calling最小实现
│   │   ├── plan_and_execute_minimal.py # Plan-and-Execute模式
│   │   └── reflection_minimal.py     # Reflection模式
│   ├── src/main/resources/           # 配置文件
│   ├── experiments/                  # RAG实验数据
│   ├── prompt_optimizer_frontend/    # Prompt优化器前端
│   └── data/                         # SQLite数据库
├── agentic-tutorial/                 # Agent教程模块
├── pom.xml                           # Maven主配置
└── README.md                         # 本文件
```

## ⭐ Mini Harness — 核心项目

拆了四大生产级Agent（OpenClaw/Hermes/Claude Code/Codex）的源码和官方文档，提炼出7条可迁移的设计原则，手敲了9个文件的迷你版Agent Harness。

详见：[mini_harness/README.md](llm-learn-app/src/main/python/mini_harness/README.md)

**7条设计原则**：

1. **串行化对话循环** — per-session队列防止竞态
2. **渐进式权限信任** — 三层分级，deny永远优先
3. **Agent主动策展记忆** — 不是被动日志，是主动笔记+容量管理
4. **Skill自描述可热加载** — SKILL.md让Agent自动发现能力
5. **自动上下文压缩** — token接近上限时自动压缩
6. **Tool自描述+ReAct循环** — LLM推理→Tool调用→观察→继续推理
7. **LLM无状态依赖注入** — system prompt注入时间等实时信息

## 🚀 快速开始

### Java项目

```bash
# 启动MySQL（Docker）
docker run -d --name llm-mysql \
  -e MYSQL_ROOT_PASSWORD=llm_learn_2026 \
  -e MYSQL_DATABASE=llm_learn \
  -p 3306:3306 mysql:8.0

# 编译运行
mvn spring-boot:run
```

### Mini Harness（Python）

```bash
cd llm-learn-app/src/main/python/mini_harness

# 配置LLM（SiliconFlow + GLM-5.1）
export LLM_API_KEY=your-api-key
export LLM_BASE_URL=https://api.siliconflow.cn/v1
export LLM_MODEL=Pro/zai-org/GLM-5.1

# 运行
python3 agent_loop.py
```

### 其他Agent示例

```bash
cd llm-learn-app/src/main/python

# 配置API Key
export SILICONFLOW_API_KEY=your-api-key

# ReAct模式
python3 react_with_real_llm.py

# Function Calling模式
python3 function_calling_minimal.py

# MCP Agent
python3 agent/mcp_agent.py
```

## 🔒 安全说明

- **所有API Key通过环境变量传入**，代码中不含硬编码密钥
- `.env`、`application-local.properties`、`setenv.sh` 已在 `.gitignore` 中排除
- 数据库密码通过环境变量 `DB_PASSWORD` 配置

## 📚 学习路径

| 阶段 | 内容 | 代码位置 |
|------|------|---------|
| **Week 1-2** | LLM基础 + Prompt Engineering | Java: Prompt优化器 |
| **Week 3-4** | RAG系统 + 向量检索 | Java: Embedding/向量数据库 |
| **Week 5-6** | Function Calling + ReAct | Python: react_minimal.py等 |
| **Week 7-8** | Agent架构 + Mini Harness | Python: mini_harness/ |

## 🛠️ 技术栈

| 层面 | 技术 |
|------|------|
| **后端** | Spring Boot 3.x + Java 17 |
| **向量数据库** | Milvus / Pinecone |
| **LLM** | SiliconFlow (GLM-5.1) / OpenAI (GPT系列) |
| **Python** | Python 3.12+ / asyncio / OpenAI SDK |
| **数据库** | MySQL 8.0 / SQLite |
| **前端** | React + Ant Design + Vite |

## 📄 License

MIT