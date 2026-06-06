# Mini Harness — 迷你版AI Agent运行时框架

> **拆了四大生产级Agent（OpenClaw/Hermes/Claude Code/Codex）的源码和官方文档，提炼出7条可迁移的设计原则，手敲了9个文件的迷你版Agent Harness。**

## 🧬 设计原则

拆解四大Agent后提炼的7条共识性设计原则——不管你用什么语言、什么框架、什么模型，这些都是做Agent时的baseline：

| # | 原则 | 来源 | 对应模块 |
|---|------|------|---------|
| 1 | **串行化对话循环** — per-session队列防止竞态 | OpenClaw | session.py |
| 2 | **渐进式权限信任** — 三层分级不一刀切，deny永远优先 | Claude Code/Codex | permissions.py |
| 3 | **Agent主动策展记忆** — 不是被动日志，是nudge驱动的主动笔记+容量管理 | Hermes | memory.py |
| 4 | **Skill自描述可热加载** — SKILL.md+agentskills.io让Agent自动发现能力 | OpenClaw/Claude Code/Codex | skills.py |
| 5 | **自动上下文压缩** — token接近上限时自动压缩历史为summary | 四大Agent都有 | compaction.py |
| 6 | **Tool自描述+ReAct循环** — LLM推理→Tool调用→观察结果→继续推理 | Claude Code/Codex | tools.py + tool_executor.py |
| 7 | **LLM无状态依赖注入** — system prompt注入时间等实时信息 | 实战踩坑 | llm_client.py |

## 📁 文件结构

```
mini_harness/
├── session.py          # Session串行化 — asyncio.Queue + is_processing锁
├── permissions.py      # 权限分级 — 三层分级 + deny优先 + 6种模式
├── memory.py           # 记忆策展 — MEMORY.md + 2000字符容量管理 + 日志 + 搜索
├── skills.py           # Skill热加载 — SKILL.md YAML frontmatter + 隐式/显式匹配
├── llm_client.py       # LLM调用 — SiliconFlow/GLM-5.1 + function calling支持
├── tools.py            # 4个内置Tool — read_file/write_file/exec_shell/search_code
├── tool_executor.py    # ReAct循环 — LLM推理→Tool调用→观察→继续推理（最多5轮）
├── compaction.py       # 上下文压缩 — token超70%自动压缩 + 手动/compact
├── agent_loop.py       # 对话循环入口 — 9步串起所有模块
├── run.py              # 非交互式测试入口
├── skills_dir/
│   └── example/
│       └── SKILL.md    # 示例Skill（code-review）
└── memory_dir/         # 记忆存储目录
    ├── MEMORY.md       # 长期记忆（2000字符上限）
    └── 日志文件        # 每日日志 YYYY-MM-DD.md
```

## 🚀 快速开始

```bash
cd mini_harness

# 交互式运行（默认配置SiliconFlow + GLM-5.1）
python3 agent_loop.py

# 自定义LLM配置
export LLM_API_KEY=your-key
export LLM_BASE_URL=https://api.siliconflow.cn/v1
export LLM_MODEL=Pro/zai-org/GLM-5.1
python3 agent_loop.py

# 非交互式测试
python3 run.py
```

## 💬 交互式命令

| 命令 | 说明 |
|------|------|
| `quit` | 退出 |
| `plan` | 切换到Plan模式（只允许只读操作） |
| `bypass` | 切换到Bypass模式（全放开，仅rm -rf拦截） |
| `default` | 切换到Default模式（三层分级权限） |
| `compact` | 手动压缩上下文（保留最近6条消息，旧消息压缩为summary） |
| `status` | 查看当前状态（权限/记忆/Skill/Tool/Compaction） |

## 🔧 9步对话循环

每条用户消息经过9步处理：

```
1. 消息到达 → Session入队（串行化）
2. 串行取出 → 同一Session同时只处理一条消息
3. 加载记忆 → MEMORY.md + 最近日志
4. Skill注入 → 根据用户输入自动匹配Skill，注入prompt
5. 权限评估 → deny→ask→allow检查
6. LLM推理 → 组装prompt → 调用GLM-5.1
7. Tool执行 → ReAct循环（最多5轮Tool调用）
8. 记忆写入 → 重要结论写入MEMORY.md或日志
8b. Compaction → token超70%自动压缩历史
9. 输出回复 → 返回给用户
```

## 🔐 权限分级设计

参考Claude Code的三层分级权限：

| 层级 | ToolLevel | 举例 | 批准要求 | "不再询问"记忆范围 |
|------|-----------|------|---------|-------------------|
| 第1层 | `READ_ONLY` | 读文件、grep搜索 | **免批** | — |
| 第2层 | `SHELL` | bash命令执行 | 需用户批准 | **永久记住**（per项目+命令） |
| 第3层 | `FILE_WRITE` | 写文件 | 需用户批准 | **仅session内记住** |

规则评估优先级：**deny → ask → allow**（deny永远最高优先级，一旦deny不可能被allow翻盘）

6种权限模式：default / plan / acceptEdits / auto / dontAsk / bypass

## 🧠 记忆策展设计

参考Hermes的nudge驱动策展：

- **MEMORY.md**（长期记忆）— Agent主动策展，有2000字符上限
- **分类记忆** — decision/lesson/fact/preference 四类
- **容量管理** — 满了不让加，必须先合并旧的再写新的
- **日志** — 每日YYYY-MM-DD.md，记录项目+标题+结论+教训+标签
- **搜索** — 关键词跨层检索（MEMORY.md + 日志）

## 🛠️ Tool系统

4个内置Tool（参考OpenClaw/Claude Code/Codex）：

| Tool | 功能 | 权限级别 | 安全检查 |
|------|------|---------|---------|
| `read_file` | 读取文件内容（支持offset/limit分段读） | read_only | 路径穿越检查（禁止..） |
| `write_file` | 写入文件（覆盖/追加） | file_write | 路径穿越检查 |
| `exec_shell` | 执行Shell命令 | shell | 危险命令拦截（rm -rf /等） |
| `search_code` | 搜索代码（grep/find） | read_only | 路径穿越检查 |

Tool调用通过OpenAI function calling格式 — LLM自动识别需要调用哪个Tool、传什么参数。

## 🔄 ReAct循环

Agent的Tool调用流程（最多5轮防止无限循环）：

```
LLM推理 → 判断是否需要Tool
    ↓ 是
解析tool_calls → 执行Tool → 得到observation
    ↓
observation回传LLM → LLM基于结果继续推理
    ↓ 可能继续调用Tool
最终输出回复
```

**实际效果**：Agent自动执行了3轮Tool调用：
1. `search_code` 找到目标文件
2. `read_file` 读前5行
3. `read_file` 读前50行（觉得5行不够，自己加量了）

## 📦 Compaction（上下文压缩）

参考Claude Code /compact + Codex compact.rs：

- **自动触发**：token超过70%阈值或消息数超30条
- **KEEP_RECENT策略**：保留最近6条消息完整，旧消息交给LLM生成summary
- **手动触发**：输入 `/compact`
- **LLM summary**：GLM-5.1生成的摘要精准提炼核心要点
- **Pre/Post Hooks**：压缩前后可执行自定义脚本

## 🎯 Skill热加载

参考OpenClaw/Claude Code/Codex的SKILL.md+agentskills.io标准：

- **YAML frontmatter**定义name/description/priority/invocation
- **invocation模式**：auto（根据description关键词自动匹配）或explicit（必须@skill-name）
- **Live change detection**：文件mtime变更自动重新加载
- **prompt注入**：匹配的Skill instructions自动拼接到Agent prompt

## 🔮 与四大Agent的对比

| 能力 | Mini Harness | 四大Agent | 下一步 |
|------|-------------|----------|--------|
| 串行化对话循环 | ✅ asyncio.Queue | ✅ 全都有 | — |
| 权限分级 | ✅ 三层分级 | ✅ 全都有 | — |
| 记忆策展 | ✅ MEMORY.md+日志 | ✅ 全都有 | — |
| Skill热加载 | ✅ SKILL.md | ✅ 全都有 | — |
| LLM调用 | ✅ GLM-5.1 | ✅ 全都有 | — |
| Tool执行 | ✅ 4个内置Tool | ✅ 全都有 | — |
| Compaction | ✅ 自动+手动 | ✅ 全都有 | — |
| 子Agent spawn | ❌ | ✅ 全都有 | 下一步 |
| MCP协议 | ❌ | ✅ 全都有 | 下一步 |
| 多通道Gateway | ❌ | ✅ OpenClaw/Hermes | 远期 |

## 📜 信息来源

| Agent | 来源 |
|-------|------|
| **OpenClaw** | 本地 docs/concepts/*.md 官方文档 |
| **Hermes** | 官方文档站 hermes-agent.nousresearch.com/docs |
| **Claude Code** | Anthropic官方文档 code.claude.com/docs/en |
| **Codex** | GitHub源码 codex-rs/（exec_policy.rs/safety.rs/compact.rs/skills.rs） |

## 📄 License

MIT