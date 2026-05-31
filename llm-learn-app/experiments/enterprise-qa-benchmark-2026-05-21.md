# 企业级RAG问答系统对比实验 - Week4 Day7 收官

**日期**: 2026-05-21  
**知识库**: 10篇企业文档 + 1篇rag-note.md（semantic切分，243 chunks）  
**三种模式**: baseline / optimized / enterprise  

---

## 一、实验设计

| 模式 | 描述 | 配置 |
|------|------|------|
| **baseline** | 纯向量检索（Week3水平） | searchMode=vector, threshold=0.6, 无压缩 |
| **optimized** | Hybrid+Rerank+压缩（Week4优化版） | hybrid_rerank, threshold=0.5, summary压缩, 无OOD检测 |
| **enterprise** | 完整企业版 | hybrid_rerank, threshold=0.5, summary压缩, OOD检测(threshold=0.4) |

### 测试集（15问）

- 🟢 精确匹配型（5问）：关键词直接命中
- 🟡 语义模糊型（5问）：需要理解意图
- 🔴 知识库外型（5问）：答案不在库中

---

## 二、核心数据

### 1. 关键词命中率（精确匹配型）

| 模式 | Q1年假 | Q2薪资 | Q3检索模式 | Q4密码 | Q5存储 | 平均 |
|------|--------|--------|-----------|--------|--------|------|
| baseline | 2/2 ✅ | 0/2 ❌ | 0/1 ❌ | 1/1 ✅ | 1/1 ✅ | **60%** |
| optimized | 1/2 | 0/2 | 0/1* | 1/1 ✅ | 1/1 ✅ | **50%** |
| enterprise | 2/2 ✅ | 0/2 ❌ | 0/1 ❌ | 1/1 ✅ | 1/1 ✅ | **60%** |

*Q3说明：optimized虽然关键词"hybrid_rerank"没命中，但答案正确（回答了"Hybrid Search + Rerank"）

### 2. 语义模糊型召回能力

| 模式 | Q6请假 | Q7保险 | Q8入职 | Q9代码评审 | Q10安全漏洞 | 拒答数 |
|------|--------|--------|--------|-----------|------------|--------|
| baseline | ❌拒答 | ✅3/3 | ❌拒答 | ❌拒答 | ❌拒答 | **4/5** |
| optimized | ✅3/3 | ✅3/3 | ✅1/3 | ✅3/3 | ✅3/3 | **0/5** |
| enterprise | ✅3/3 | ⚠️OOD | ✅1/3 | ✅3/3 | ✅3/3 | **0/5**（Q7误判OOD） |

**关键发现**：baseline纯向量检索在语义模糊问题上4/5拒答，optimized全部能答。hybrid_rerank召回能力碾压纯向量。

### 3. 知识库外检测

| 模式 | Q11营收 | Q12CEO | Q13车位 | Q14股票 | Q15菜单 | 拒绝率 |
|------|---------|---------|---------|---------|---------|--------|
| baseline | 拒答✅ | 拒答✅ | 拒答✅ | 拒答✅ | 拒答✅ | **100%**（LLM判断） |
| optimized | 拒答✅ | 拒答✅ | 拒答✅ | 拒答✅ | 拒答✅ | **100%**（LLM判断） |
| enterprise | OOD✅ | OOD✅ | OOD✅ | OOD✅ | OOD✅ | **100%**（API标记） |

**关键差异**：enterprise模式在OOD问题上响应时间仅0.6-0.8秒（检索阶段就拦截，不调LLM），而baseline/optimized需10-15秒（先检索再让LLM判断）。

### 4. 平均响应时间

| 模式 | 知识库内问题 | 知识库外问题 | 总平均 |
|------|-------------|-------------|--------|
| baseline | 7.2s | 0.3s | 5.3s |
| optimized | 17.8s | 12.7s | 17.8s |
| enterprise | 22.3s | **0.7s** | 12.8s |

**关键发现**：enterprise的OOD检测让知识库外问题响应从12秒降到0.7秒，**快18倍**。

### 5. 置信度对比

| 模式 | 平均confidence |
|------|---------------|
| baseline | 0（不提供confidence字段） |
| optimized | 0.4971 |
| enterprise | 0.4397 |

---

## 三、问题与Bug

### Q3 "NovaRAG默认检索模式" — enterprise模式ERROR
- enterprise模式Q3查询返回ERROR（可能是hybrid_rerank检索阶段Rerank API超时）
- 优化版则成功返回正确答案（说明Rerank服务稳定性是个问题）

### Q7 "公司有没有商业保险" — enterprise误判OOD
- confidence=0.0801，低于outOfDomainThreshold(0.4)，被enterprise模式误判为知识库外
- 实际上知识库里有完整答案（意外险50万+补充医疗+重大疾病险）
- **原因**：hybrid_rerank检索后Rerank精排给了较低的relevanceScore，误触发OOD拒绝
- **这是今天最大的Bug**，需要修正

### baseline模式confidence=0
- baseline用的是旧的/rag/query接口，返回RagQueryResponse不含confidence字段
- 不是bug，是接口设计差异

---

## 四、Week4 7天实验结论汇总

| Day | 优化项 | 结论 |
|-----|--------|------|
| D1 | threshold | 0.50最优，0.35太宽0.65误伤0.80失明 |
| D2 | BM25+Hybrid | Hybrid召回覆盖面最广，小知识库增益显著 |
| D3 | Rerank | 精排提升显著，但服务稳定性需关注 |
| D4 | Semantic切分 | THRESHOLD=0.5最优21 chunks，质量远超固定切分 |
| D5 | 上下文压缩 | summary模式减少噪声，长文档必用 |
| D6 | 多路召回 | 小知识库第三路(exactMatch)无额外增益，≈hybrid_rerank |
| D7 | OOD检测 | ✅知识库外响应快18倍，❌误判率需修正threshold |

---

## 五、企业级RAG最小必要配置（别过度工程）

基于7天实验数据，**能跑起来的最小配置**是：

```
1. Hybrid Search（vector + BM25 + RRF）  ← 必选，召回能力碾压纯向量
2. threshold = 0.5                        ← 必选，过滤低质量检索
3. 固定切分（上线先保稳）                  ← 必选，semantic是高级选项
4. topK = 5                               ← 必选，5个候选足够
```

**可选优化**（有成本但增益有限）：
- Rerank：提升精确率，但增加10秒+依赖外部API稳定性
- 上下文压缩：长文档场景有用，短文档没必要
- OOD检测：**概念正确但threshold需谨慎调**，0.4太低会误判

**不需要的**：
- 多路召回第三路（exactMatch）：小知识库≈0增益
- Semantic切分：质量好但启动太慢，上线不实用

---

## 六、下周展望 — Week5 Agent智能体开发

Week4收官。RAG这条线从基础原理到企业级实战，7天从0到1打穿了。

**Week5方向**：从"被动检索"到"主动执行"——Agent能调工具、做决策、多步推理。

核心学习点：
1. Agent架构：感知→规划→执行→反思
2. Function Calling：让LLM调用外部工具
3. LangChain4j Agent实战：查天气+查日程+算数据的智能助手

---

**实验数据**: `experiments/enterprise-qa-benchmark-2026-05-21.json`  
**代码变更**: EnterpriseRagConfig + EnterpriseRagResponse + enterpriseQuery + 批量加载接口