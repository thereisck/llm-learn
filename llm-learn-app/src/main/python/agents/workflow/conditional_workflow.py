"""
条件分支Workflow：确定性路由
核心概念：代码判断走哪条分支，不是LLM决定

三种Agent：
- CodeAgent：处理代码相关任务（写代码、解释代码、修Bug）
- TextAgent：处理文本相关任务（写文章、翻译、润色）
- MathAgent：处理数学相关任务（计算、推理、公式推导）

Router用代码判断：if "代码" in task → CodeAgent
这是确定性路由——你写if/else，Agent没有自主权

对比Supervisor模式（Week5 Day1的ReAct）：
- Conditional：代码决定路径 → 100%可控，但只能走预设路
- Supervisor：LLM自己决策 → 可能选错，但能走新路

Java直觉：Conditional = Spring的RequestMapping（路径确定）
         Supervisor = Spring的AOP动态代理（运行时决策）
"""
import os
import json
import sys

# 导入mini_harness的LLM客户端（路径：从workflow目录跳到mini_harness）
sys.path.append(os.path.join(os.path.dirname(__file__), "..", "..", "mini_harness"))
from llm_client import LLMClient

# ============================================================
# 第一段：BaseAgent基类 + Router路由器
# ============================================================

class BaseAgent:
    """
    Agent基类 — 每个Agent有独立prompt + 输入输出约定
    
    设计原则：
    - 独立prompt：每个Agent只看自己的system prompt + 本次输入
    - 上下文隔离：Agent之间不共享对话历史
    - 简洁输出：要求Agent输出精简，避免上下文膨胀
    
    对比Week5的多Agent：这里没有AgenticScope，Agent之间靠Router传递结果
    """

    def __init__(self, name: str, system_prompt: str, llm: LLMClient):
        self.name = name
        self.system_prompt = system_prompt
        self.llm = llm

    def invoke(self, task: str) -> str:
        """
        执行任务 — 只用system_prompt + task，不携带历史
        
        设计原则：单轮调用，输出即结果 — 简洁可控
        
        Args:
            task: 任务描述
            
        Returns:
            Agent的输出文本
        """
        messages = [
            {"role": "system", "content": self.system_prompt},
            {"role": "user", "content": task},
        ]
        result = self.llm.chat(messages, max_tokens=1000)
        print(f"[{self.name}] 输出完成，长度={len(result)}字")
        return result
    
class ConditionalRouter:
    """
    确定性路由器 — 用代码判断走哪条分支
    
    核心区别：这是if/else判断，不是LLM自主决策
    - 你预设了哪些分支（code/text/math）
    - 你写了关键词映射规则
    - LLM没有任何选择权
    
    对比Supervisor模式：
    - Supervisor = 让LLM自己选Agent → 可能选错
    - Conditional = 你写规则选Agent → 100%可控
    
    Java直觉：就像Controller层的RequestMapping — 请求路径确定，方法确定
    """

    def __init__(self):
        # 分支规则：关键词 → Agent名称
        # 这就是"确定性路由"的核心——你写好了规则，代码执行
        self.rules = {
            "code": "code_agent",     # 代码相关 → CodeAgent
            "代码": "code_agent",     # 中文关键词
            "bug": "code_agent",      # Bug修复 → 也走CodeAgent
            "bug修复": "code_agent",
            "text": "text_agent",     # 文本相关 → TextAgent
            "文本": "text_agent",
            "文章": "text_agent",
            "翻译": "text_agent",
            "润色": "text_agent",
            "math": "math_agent",     # 数学相关 → MathAgent
            "数学": "math_agent",
            "计算": "math_agent",
            "公式": "math_agent",
            "推理": "math_agent",
        }
        self.agents = {}  # name → Agent实例

    def register(self, name: str, agent: BaseAgent):
        """注册Agent到路由表"""
        self.agents[name] = agent

    def route(self, task: str) -> str:
        """
        路由决策 — 纯代码判断，不调LLM
        
        逻辑：遍历规则，找到第一个匹配的关键词 → 返回对应Agent名
        如果没有匹配 → 返回默认Agent
        
        这就是"确定性路由"：代码说了算，LLM没有发言权
        
        Args:
            task: 任务描述
            
        Returns:
            匹配的Agent名称
        """
        for keyword, agent_name in self.rules.items():
            if keyword in task.lower():
                return agent_name
        # 默认路由 — 没匹配到关键词时走TextAgent（万能兜底）
        return "text_agent"

    def run(self, task: str) -> dict:
        """
        执行完整路由流程：判断 → 选择 → 执行
        
        返回dict包含：
        - task: 原始任务
        - agent: 选择的Agent名
        - keyword: 匹配的关键词
        - result: Agent输出
        
        设计原则：返回完整信息方便调试 — 知道为什么选了这条路
        """
        agent_name = self.route(task)

        # 找匹配的关键词（方便调试看"为什么走这条路"）
        matched_keyword = "default"
        for keyword, name in self.rules.items():
            if keyword in task.lower() and name == agent_name:
                matched_keyword = keyword
                break

        # 执行选中的Agent
        agent = self.agents[agent_name]
        result = agent.invoke(task)

        return {
            "task": task,
            "agent": agent_name,
            "keyword": matched_keyword,
            "result": result,
        }

# ============================================================
# 第二段：三个具体Agent（先敲完第一段，跑通后再敲这段）
# ============================================================
def create_agents(llm: LLMClient) -> dict:
    """
    创建三个Agent实例
    
    每个Agent有独特的system_prompt — 这是Agent的"性格"
    prompt设计原则：明确角色 + 输出格式要求 + 质量约束
    """

    code_agent = BaseAgent(
        name="CodeAgent",
        system_prompt=(
            "你是一个代码专家。只处理代码相关任务：写代码、解释代码、修复Bug。\n"
            "输出要求：\n"
            "1. 直接给出代码或解决方案，不要废话\n"
            "2. 代码用markdown代码块包裹\n"
            "3. 如果是Bug修复，先说原因再说方案\n"
            "4. 控制输出在500字以内"
        ),
        llm=llm,
    )

    text_agent = BaseAgent(
        name="TextAgent",
        system_prompt=(
            "你是一个文本处理专家。只处理文本相关任务：写文章、翻译、润色。\n"
            "输出要求：\n"
            "1. 直接输出处理后的文本\n"
            "2. 保持原文风格，不要过度改写\n"
            "3. 翻译时保留技术术语的原文\n"
            "4. 控制输出在500字以内"
        ),
        llm=llm,
    )

    math_agent = BaseAgent(
        name="MathAgent",
        system_prompt=(
            "你是一个数学推理专家。只处理数学相关任务：计算、推理、公式推导。\n"
            "输出要求：\n"
            "1. 先写推理步骤，再给最终答案\n"
            "2. 公式用markdown格式\n"
            "3. 每步推理必须验证\n"
            "4. 控制输出在500字以内"
        ),
        llm=llm,
    )

    return {
        "code_agent": code_agent,
        "text_agent": text_agent,
        "math_agent": math_agent,
    }
    
# ============================================================
# 第三段：主函数 + 测试场景（先敲完第二段，跑通后再敲这段）
# ============================================================

def main():
    """测试条件分支Workflow"""

    # 初始化LLM客户端（环境变量配置：LLM_API_KEY, LLM_BASE_URL, LLM_MODEL）
    llm = LLMClient()

    # 创建Agent
    agents = create_agents(llm)

    # 创建Router并注册Agent
    router = ConditionalRouter()
    for name, agent in agents.items():
        router.register(name, agent)

    # ============================================================
    # 测试场景1：代码任务 → 应该路由到CodeAgent
    # ============================================================
    print("=" * 60)
    print("测试1：代码任务 → 预期路由到 CodeAgent")
    print("=" * 60)

    result = router.run("帮我写一段代码，实现快速排序算法")
    print(f"路由结果: agent={result['agent']}, keyword={result['keyword']}")
    print(f"Agent输出:\n{result['result']}")
    print()

    # ============================================================
    # 测试场景2：文本任务 → 应该路由到TextAgent
    # ============================================================
    print("=" * 60)
    print("测试2：文本任务 → 预期路由到 TextAgent")
    print("=" * 60)

    result = router.run("翻译这段文本：The quick brown fox jumps over the lazy dog")
    print(f"路由结果: agent={result['agent']}, keyword={result['keyword']}")
    print(f"Agent输出:\n{result['result']}")
    print()

    # ============================================================
    # 测试场景3：数学任务 → 应该路由到MathAgent
    # ============================================================
    print("=" * 60)
    print("测试3：数学任务 → 预期路由到 MathAgent")
    print("=" * 60)

    result = router.run("计算数学推理：证明根号2是无理数")
    print(f"路由结果: agent={result['agent']}, keyword={result['keyword']}")
    print(f"Agent输出:\n{result['result']}")
    print()

    # ============================================================
    # 测试场景4：不匹配任何关键词 → 应该走默认路由（TextAgent）
    # ============================================================
    print("=" * 60)
    print("测试4：未知任务 → 预期路由到 TextAgent（默认兜底）")
    print("=" * 60)

    result = router.run("今天天气怎么样？")
    print(f"路由结果: agent={result['agent']}, keyword={result['keyword']}")
    print(f"Agent输出:\n{result['result']}")
    print()

    # ============================================================
    # 总结：条件分支 vs Supervisor的关键差异
    # ============================================================
    print("=" * 60)
    print("核心洞察：条件分支 vs Supervisor")
    print("=" * 60)
    print("""
    Conditional（今天学的）：
      - 路由决策 = 代码if/else → 100%可控
      - 只能走预设路 → 新类型任务无法处理
      - 适合：已知分支类型，业务流程固定

    Supervisor（Week5 Day1的ReAct）：
      - 路由决策 = LLM自己选 → 可能选错
      - 能走新路 → 处理未预见的情况
      - 适合：不确定任务类型，需要灵活应对

    实践选择：能用确定性路由就用确定性路由
              只有不确定时才让LLM自主决策
              （确定性 > 灵活性，除非你真的需要灵活）
    """)


if __name__ == "__main__":
    main()