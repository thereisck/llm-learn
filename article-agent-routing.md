---
title: Agent路由该让LLM自己选还是代码说了算？三种模式实战对比
author: CK码农茶馆
cover: /Users/zhiweizhang/Desktop/封面图/cover-agent-routing.png
tags: [Agent, Workflow, 条件分支, 循环迭代, LLM]
---

Agent的路由决策到底该谁说了算？

让LLM自己选Agent，看着挺智能，实际上选错了你连纠错的机会都没有。让代码用if/else硬路由，听着不够灵活，但100%可控，选错了你能马上定位。

今天手敲了两个Workflow——Conditional（确定性路由）和Looping（阈值循环迭代），跑通后发现一个挺反直觉的结论：**能用确定性路由就用确定性路由，LLM自主决策只在不确定时才该用。**

这结论不是我拍脑袋想的，是代码跑出来的。下面把三种模式掰开揉碎对比，每种都有真实代码可跑。

## 三种路由模式，一次搞明白

Agent世界里，任务怎么分配给不同的Agent处理，核心就三种模式：

**1. Conditional（条件分支）**——代码写好if/else规则，关键词匹配走哪条路。你写`if "代码" in task → CodeAgent`，这就是确定性路由，LLM没有发言权。

**2. Supervisor（监督者）**——让一个LLM当调度员，自己判断任务该分配给谁。Week5学的ReAct就是这个思路：LLM自主决策，可能选错但能处理未预见的情况。

**3. Looping（循环迭代）**——不走路由，走循环。Editor写稿→Reviewer评分→不达标就改→再评分，代码用阈值（score>=7）判断退出，不是LLM说"好了就行"。

三种模式不是互相替代的，是互补的。关键问题不是"哪个更好"，而是"啥时候该用哪个"。

## Conditional实战：关键词路由，代码说了算

Conditional Workflow的核心逻辑特别简单——一个路由表，关键词匹配到哪个Agent就走哪条路。没有LLM参与决策，纯代码判断。

看这段Router核心代码：

```python
class ConditionalRouter:
    def __init__(self):
        # 关键词 → Agent名称，确定性路由的全部逻辑
        self.rules = {
            "code": "code_agent",
            "代码": "code_agent",
            "bug": "code_agent",
            "text": "text_agent",
            "翻译": "text_agent",
            "math": "math_agent",
            "推理": "math_agent",
        }
        self.agents = {}

    def route(self, task: str) -> str:
        # 遍历规则，第一个匹配的关键词就返回对应Agent
        for keyword, agent_name in self.rules.items():
            if keyword in task.lower():
                return agent_name
        # 没匹配到？走默认Agent兜底
        return "text_agent"
```

这段代码做了啥？遍历rules字典，看task里有没有匹配的关键词，有就走对应的Agent，没有就走默认兜底。

**没有LLM参与**。路由决策100%是代码做的，可追溯、可调试、可预测。

Java后端的同学一定觉得亲切——跟Spring的RequestMapping一模一样：请求路径确定，方法确定，不存在运行时才发现"原来这个请求应该走另一个Controller"。

跑一下测试看路由结果：

```python
router.run("帮我写一段代码，实现快速排序")
# → agent=code_agent, keyword=代码 ✅

router.run("翻译这段文本：The quick brown fox")
# → agent=text_agent, keyword=翻译 ✅

router.run("计算数学推理：证明根号2是无理数")
# → agent=math_agent, keyword=数学 ✅

router.run("今天天气怎么样？")
# → agent=text_agent, keyword=default ✅ 兜底
```

四个场景路由结果完全一致预期。确定性路由的价值：**你提前知道每个任务走哪条路，不存在LLM选错你还不知道的情况**。

但Conditional有明显短板：**只能走预设路**。没定义关键词的任务类型只能走默认兜底。这就是Supervisor存在的意义。

## Supervisor对比：让LLM自己选，看起来智能实则危险

Week5 Day1学了ReAct——LLM自主决定"该调用什么工具""下一步做什么"。看起来挺智能的，LLM能根据上下文灵活决策，但实际跑起来有几个坑：

1. **选错了你不知道**——LLM把"写代码"的任务分给了TextAgent，你从日志里看到它走了text_agent，但你可能没发现这其实该走code_agent
2. **不可复现**——同一个任务跑两次，LLM可能选不同的路。调试的时候你看到它第一次选对了，第二次选错了，没法稳定复现
3. **成本不可控**——每次路由决策本身就要调一次LLM，如果你的路由表有10个分支，每次决策都是一个Chat Completion请求

Java直觉：Supervisor就像AOP动态代理——运行时才决定走哪个切面，你没法在编译期确定执行路径。

**关键结论**：Supervisor适合不确定任务类型的场景（比如客服对话，用户可能问任何问题）。但如果你的业务流程是固定的（代码审查流水线、内容生产流水线），Conditional比Supervisor更靠谱。

## Looping实战：阈值循环迭代，代码判断退出条件

Looping模式是今天的重头戏。跟Week5的Reflection对比一下就明白了：

**Reflection**：LLM做完任务，自己评估"满意吗？"——LLM说了算。可能自评满意但实际不好，也可能自评不满意但结果其实还行。

**Looping**：Editor写稿→Reviewer评分→不达标就循环改进——**代码判断score>=threshold就退出**。退出条件是确定性的。

核心代码看LoopingWorkflow的run方法：

```python
class LoopingWorkflow:
    def __init__(self, editor, reviewer, threshold=7, max_iterations=3):
        self.editor = editor
        self.reviewer = reviewer
        self.threshold = threshold      # 达标阈值：7分以上算合格
        self.max_iterations = max_iterations  # 最大循环次数：防死循环

    def run(self, topic: str) -> dict:
        for i in range(self.max_iterations):
            # 第一轮：Editor写初稿
            if i == 0:
                draft = self.editor.invoke(task=topic)
            # 循环中：根据Reviewer意见改进
            else:
                feedback_text = self._format_feedback(review)
                draft = self.editor.invoke(
                    task=topic, feedback=feedback_text, previous_draft=draft)

            # Reviewer评分
            review = self.reviewer.invoke(draft=draft, topic=topic)
            score = review.get("score", 0)

            # 代码判断退出条件
            if score >= self.threshold:
                print(f"✅ 达标！score={score} >= threshold={self.threshold}")
                break
            else:
                print(f"❌ 未达标 score={score} < threshold={self.threshold}")

        return {"success": score >= self.threshold, "final_score": score}
```

三个关键设计点：

**1. threshold是代码判断的阈值**——不是LLM说"好了就行"，是score>=7才算达标。这个阈值你可以调，调到5就宽松，调到9就严格，但退出条件永远是你设定的数字。

**2. max_iterations防死循环**——最多循环3次，不管有没有达标都强制结束。不然Reviewer永远给5分、Editor永远改不到位，就死循环了。

**3. feedback格式化**——Reviewer输出的是JSON（score/issues/suggestions），但传给Editor时要转成自然语言。LLM读自然语言比读JSON更高效。

## 实战踩坑：一个ModuleNotFoundError教会我的事

跑第一个代码的时候直接报错：

```
ModuleNotFoundError: No module named 'llm_client'
```

原因特别简单——我把代码放到了`agents/workflow/`子目录，但import路径还写着`../mini_harness`，实际需要跳两层`../../mini_harness`。

这坑看起来很小，但暴露了一个设计问题：**目录结构变了，import路径必须跟着变**。Java里你改包名要改import，Python里你改目录层级也要改sys.path.append。

修复方法：

```python
# 原路径（在src/main/python/下）：
sys.path.append(os.path.join(os.path.dirname(__file__), "..", "mini_harness"))

# 新路径（在agents/workflow/下）：
sys.path.append(os.path.join(os.path.dirname(__file__), "..", "..", "mini_harness"))
```

**教训**：Python的sys.path.append是运行时hack，不如Java的包管理优雅。但好处是灵活——你不需要改PYTHONPATH环境变量，代码里直接动态拼接就行。

## JSON解析容错：LLM永远不会完美输出JSON

Reviewer要求输出纯JSON，但LLM经常给你带前缀的：

```
以下是评分：
{"score": 7, "issues": ["逻辑不清晰"], "suggestions": ["加强论证"]}
```

直接json.loads就炸了。所以ReviewerAgent的_parse_review方法做了三级容错：

1. 直接json.loads整个字符串——最理想的情况
2. 正则提取第一个`{...}`块——LLM加了前缀文字
3. 正则提取score数字——JSON格式完全乱了但score还在
4. 全失败返回默认评分1——解析错误不能阻塞循环

```python
def _parse_review(self, raw: str) -> dict:
    # 尝试1：直接parse
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        pass

    # 尝试2：提取{...}块
    match = re.search(r'\{[^{}]*\}', raw)
    if match:
        try:
            return json.loads(match.group())
        except json.JSONDecodeError:
            pass

    # 尝试3：只提取score
    score_match = re.search(r'"score"\s*:\s*(\d+)', raw)
    if score_match:
        return {"score": int(score_match.group(1)), "issues": ["JSON解析失败"], "suggestions": []}

    # 全失败：返回最低分，不阻塞循环
    return {"score": 1, "issues": ["审稿输出格式异常"], "suggestions": ["请重新审稿"]}
```

这个三级容错不是过度设计——实际跑的时候，GLM-5.1经常输出带中文前缀的JSON，第一种方式大概率失败，第二种才是主力。

**Java直觉**：这跟解析HTTP响应的Content-Type一样——你不能假设对方永远给你标准的application/json，要做容错处理。

## 三种模式对比总结

| 维度 | Conditional | Supervisor | Looping |
|------|-------------|------------|---------|
| 路由决策 | 代码if/else | LLM自己选 | 不走路由，循环迭代 |
| 可控性 | ✅ 100%可控 | ❌ 可能选错 | ✅ 阈值可控 |
| 可复现 | ✅ 同输入同输出 | ❌ 同输入可能不同输出 | ✅ 阈值固定 |
| 灵活性 | ❌ 只能走预设路 | ✅ 能走新路 | ✅ 每次改进方向不同 |
| Token成本 | 低（0次路由LLM调用） | 高（每次路由1次LLM调用） | 高（每轮2次LLM调用） |
| 适用场景 | 已知分支类型 | 不确定任务类型 | 质量改进 |
| Java类比 | RequestMapping | AOP动态代理 | while循环+阈值退出 |

**实践铁律**：能用Conditional就用Conditional，只有真正不确定时才用Supervisor，质量敏感时叠加Looping。

这不是拍脑袋的结论，是Anthropic那篇"Building Effective Agents"说的——Workflow模式（确定性路由）比Autonomous Agent模式（LLM自主决策）更适合大多数生产场景。

## 组合才是王道

单独用任何一种模式都有短板。实战中最好的方案是组合：

**Conditional + Looping**——先用Conditional路由到正确的Agent，再用Looping保证输出质量。

比如代码审查流水线：

1. Conditional路由——任务包含"代码"关键词→走CodeReviewer Agent
2. Looping迭代——CodeReviewer输出后，SecurityScanner再审查，不达标就循环改进
3. 代码判断退出——score>=8分才算通过

这个组合把确定性路由和阈值迭代叠在一起，既可控又质量保证。

Java直觉：这就是Spring MVC + Retry Template的组合——RequestMapping保证请求走到正确的Controller，Retry保证出错了有兜底。

下一篇预告：LangGraph4j入门——用状态图实现更复杂的Agent流程，条件边和循环边可视化编排。欢迎关注公众号「CK码农茶馆」，持续更新LLM实战系列。