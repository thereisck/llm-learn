"""
微调决策矩阵 - Week7 Day1 第2步
全量微调 vs LoRA vs QLoRA 适用场景决策引擎

不依赖任何外部API，纯Python逻辑跑通
"""

from dataclasses import dataclass, field
from typing import Optional


# ========== 数据模型 ==========

@dataclass
class FinetuneMethod:
    """一种微调方法的完整画像"""
    name: str
    full_name: str

    # 资源门槛
    min_gpu_memory_gb: float        # 最低GPU显存要求
    typical_gpu_memory_gb: float    # 典型配置显存
    trainable_params_ratio: str     # 可训练参数占比描述
    actual_trainable_pct: float     # 可训练参数占比百分比

    # 效果
    performance_score: float        # 效果评分(1-10)
    stability_score: float          # 稳定性评分(1-10)

    # 速度
    training_speed: str             # 训练速度描述
    inference_overhead: str         # 推理开销描述

    # 适用场景标签
    best_for: list[str]             # 最适合的场景列表
    avoid_when: list[str]           # 应避免的场景列表

    # 一句话总结
    summary: str


@dataclass
class UserScenario:
    """用户实际场景输入"""
    gpu_memory_gb: float            # 你有多少GPU显存
    data_size: str                  # 数据量: "small(<1k)", "medium(1k-10k)", "large(>10k)"
    task_type: str                  # 任务类型: "style", "domain", "task", "safety"
    need_max_performance: bool      # 是否需要极致效果
    budget_limited: bool            # 预算是否有限
    model_size: str                 # 模型大小: "small(<1B)", "medium(1B-7B)", "large(7B-70B)", "huge(>70B)"


@dataclass
class Recommendation:
    """推荐结果"""
    method: str
    reason: str
    confidence: float               # 推荐置信度(0-1)
    warnings: list[str] = field(default_factory=list)
    alternatives: list[str] = field(default_factory=list)


# ========== 三种微调方法画像 ==========

FULL_FINETUNE = FinetuneMethod(
    name="Full",
    full_name="全量微调 (Full Fine-tuning)",
    min_gpu_memory_gb=24,
    typical_gpu_memory_gb=80,
    trainable_params_ratio="100%",
    actual_trainable_pct=100.0,
    performance_score=9.5,
    stability_score=8.0,
    training_speed="慢(全参数更新)",
    inference_overhead="无(模型本身就是完整的)",
    best_for=[
        "极致效果需求",
        "大模型+充足GPU",
        "深度领域适配",
        "任务类型转换(如翻译→摘要)",
    ],
    avoid_when=[
        "GPU不足24GB",
        "数据少于1000条",
        "只想调整风格/语气",
        "预算有限",
        "模型>7B且只有1张卡",
    ],
    summary="全量微调效果最强，但资源门槛极高——7B模型至少需要24GB显存，70B模型需要多卡并行。适合有A100/H100集群的团队。",
)

LORA = FinetuneMethod(
    name="LoRA",
    full_name="LoRA (Low-Rank Adaptation)",
    min_gpu_memory_gb=8,
    typical_gpu_memory_gb=16,
    trainable_params_ratio="0.1%-1%",
    actual_trainable_pct=0.5,
    performance_score=8.5,
    stability_score=9.0,
    training_speed="快(只更新低秩矩阵)",
    inference_overhead="极小(合并权重后无额外开销)",
    best_for=[
        "风格/语气调整",
        "特定领域适配",
        "中等规模任务",
        "多任务切换(多个LoRA适配器)",
        "快速迭代实验",
    ],
    avoid_when=[
        "需要根本性能力改变",
        "任务与预训练差异极大",
        "追求极致效果(差Full约5-10%)",
    ],
    summary="LoRA是性价比之王——只训练0.1%参数就能达到85-95%的全量微调效果。8GB显存就能跑7B模型，合并权重后推理零开销。",
)

QLORA = FinetuneMethod(
    name="QLoRA",
    full_name="QLoRA (Quantized LoRA)",
    min_gpu_memory_gb=4,
    typical_gpu_memory_gb=6,
    trainable_params_ratio="0.1%-1%(与LoRA相同)",
    actual_trainable_pct=0.5,
    performance_score=8.0,
    stability_score=7.5,
    training_speed="比LoRA慢(量化/反量化开销)",
    inference_overhead="小(需反量化或直接用4bit推理)",
    best_for=[
        "GPU极度受限(4-6GB)",
        "消费级显卡(RTX3060/4060)",
        "个人开发者/学生",
        "快速验证想法",
        "大模型+小显存(70B+单卡24GB)",
    ],
    avoid_when=[
        "需要极致稳定性",
        "量化精度损失不可接受",
        "推理延迟敏感场景",
        "训练数据质量极高且追求最佳效果",
    ],
    summary="QLoRA让4GB显存也能微调7B模型——用4bit量化压缩基础模型，LoRA只训练增量。效果比LoRA略低(约2-5%)，但打开了消费级硬件的大门。",
)

ALL_METHODS = [FULL_FINETUNE, LORA, QLORA]


# ========== 决策引擎 ==========

class DecisionEngine:
    """基于规则的微调方法推荐引擎"""

    def recommend(self, scenario: UserScenario) -> Recommendation:
        """根据用户场景推荐最合适的微调方法"""

        scores = {}
        reasons = {}
        warnings = {}
        alternatives = {}

        for method in ALL_METHODS:
            score = 0.0
            reason_parts = []
            warn_parts = []

            # 1. GPU显存匹配(最关键，权重最高)
            if scenario.gpu_memory_gb >= method.min_gpu_memory_gb:
                score += 40
                reason_parts.append(f"显存{scenario.gpu_memory_gb}GB≥{method.min_gpu_memory_gb}GB门槛✅")
            else:
                score -= 100  # 显存不足直接扣大分
                warn_parts.append(f"⚠ 显存不足！需要{method.min_gpu_memory_gb}GB，你只有{scenario.gpu_memory_gb}GB")

            # 2. 数据量匹配
            data_match = self._score_data_size(scenario.data_size, method)
            score += data_match["score"]
            reason_parts.extend(data_match["reasons"])
            warn_parts.extend(data_match["warnings"])

            # 3. 任务类型匹配
            task_match = self._score_task_type(scenario.task_type, method)
            score += task_match["score"]
            reason_parts.extend(task_match["reasons"])

            # 4. 效果需求
            if scenario.need_max_performance:
                score += method.performance_score * 3  # 效果需求强→效果评分权重放大
            else:
                score += method.performance_score * 1

            # 5. 预算限制
            if scenario.budget_limited:
                # 预算有限→低成本方法加分
                if method.actual_trainable_pct < 1:
                    score += 15
                    reason_parts.append("预算有限→低参数方法性价比高✅")
                else:
                    score -= 10
                    warn_parts.append("⚠ 全量微调成本高，预算有限时慎选")

            # 6. 模型大小匹配
            model_match = self._score_model_size(scenario.model_size, method, scenario.gpu_memory_gb)
            score += model_match["score"]
            reason_parts.extend(model_match["reasons"])
            warn_parts.extend(model_match["warnings"])

            scores[method.name] = score
            reasons[method.name] = reason_parts
            warnings[method.name] = warn_parts

        # 选最高分
        best_name = max(scores, key=scores.get)
        best_score = scores[best_name]

        # 计算置信度
        sorted_scores = sorted(scores.values(), reverse=True)
        gap = sorted_scores[0] - sorted_scores[1] if len(sorted_scores) > 1 else 100
        confidence = min(gap / 50, 1.0)  # 分差越大→置信度越高

        # 备选方案(分数差距不大的其他方法)
        alt_names = [
            name for name, s in scores.items()
            if name != best_name and s > best_score - 30
        ]

        return Recommendation(
            method=best_name,
            reason=" | ".join(reasons[best_name]),
            confidence=confidence,
            warnings=warnings[best_name],
            alternatives=alt_names,
        )

    def _score_data_size(self, data_size: str, method: FinetuneMethod) -> dict:
        result = {"score": 0, "reasons": [], "warnings": []}

        if data_size == "small(<1k)":
            if method.actual_trainable_pct >= 100:
                result["score"] -= 15
                result["warnings"].append("⚠ 数据太少+全量微调=容易过拟合")
            else:
                result["score"] += 10
                result["reasons"].append("小数据量→LoRA/QLoRA更稳定✅")

        elif data_size == "medium(1k-10k)":
            result["score"] += 10
            result["reasons"].append("中等数据量→所有方法都适用✅")

        elif data_size == "large(>10k)":
            if method.actual_trainable_pct >= 100:
                result["score"] += 10
                result["reasons"].append("大数据量→全量微调能充分学习✅")
            else:
                result["score"] += 5
                result["reasons"].append("大数据量→LoRA也能学，但全量微调上限更高")

        return result

    def _score_task_type(self, task_type: str, method: FinetuneMethod) -> dict:
        result = {"score": 0, "reasons": []}

        task_method_map = {
            "style":   {"LoRA": 20, "QLoRA": 15, "Full": 5},
            "domain":  {"LoRA": 15, "QLoRA": 10, "Full": 20},
            "task":    {"LoRA": 10, "QLoRA": 5,  "Full": 25},
            "safety":  {"LoRA": 15, "QLoRA": 10, "Full": 20},
        }

        if task_type in task_method_map:
            result["score"] = task_method_map[task_type].get(method.name, 0)
            if result["score"] >= 20:
                result["reasons"].append(f"任务类型'{task_type}'→{method.full_name}最优✅")
            elif result["score"] >= 10:
                result["reasons"].append(f"任务类型'{task_type}'→{method.full_name}可用")

        return result

    def _score_model_size(self, model_size: str, method: FinetuneMethod, gpu_gb: float) -> dict:
        result = {"score": 0, "reasons": [], "warnings": []}

        # 大模型+小显存→QLoRA是唯一选择
        if model_size in ("large(7B-70B)", "huge(>70B)") and gpu_gb < 24:
            if method.name == "QLoRA":
                result["score"] += 30
                result["reasons"].append(f"大模型{model_size}+小显存{gpu_gb}GB→QLoRA唯一可跑✅")
            elif method.name == "LoRA":
                result["score"] -= 20
                result["warnings"].append(f"⚠ LoRA跑{model_size}至少需要16GB+，你的{gpu_gb}GB可能不够")
            else:
                result["score"] -= 100
                result["warnings"].append(f"⚠ 全量微调{model_size}需要多卡集群，单卡{gpu_gb}GB完全不够")

        # 大模型+大显存→LoRA性价比高
        elif model_size in ("large(7B-70B)", "huge(>70B)") and gpu_gb >= 24:
            if method.name == "LoRA":
                result["score"] += 15
                result["reasons"].append(f"大模型+充足显存→LoRA性价比最优✅")
            elif method.name == "Full":
                result["score"] += 10
                result["reasons"].append(f"大模型+充足显存→全量微调上限最高")

        # 小模型→全量微调可行
        elif model_size == "small(<1B)":
            if method.name == "Full":
                result["score"] += 10
                result["reasons"].append(f"小模型→全量微调成本可接受✅")

        return result

    def print_comparison_table(self):
        """打印三种方法的对比矩阵表"""
        print("\n" + "=" * 80)
        print("微调方法决策矩阵 - 三种方法全景对比")
        print("=" * 80)

        headers = ["维度", "全量微调", "LoRA", "QLoRA"]
        col_width = 18

        print(f"| {headers[0]:<14} | {headers[1]:<{col_width}} | {headers[2]:<{col_width}} | {headers[3]:<{col_width}} |")
        print(f"|{'─'*16}|{'─'*20}|{'─'*20}|{'─'*20}|")

        rows = [
            ("最低显存",    f"{FULL_FINETUNE.min_gpu_memory_gb}GB",  f"{LORA.min_gpu_memory_gb}GB",    f"{QLORA.min_gpu_memory_gb}GB"),
            ("典型显存",    f"{FULL_FINETUNE.typical_gpu_memory_gb}GB", f"{LORA.typical_gpu_memory_gb}GB", f"{QLORA.typical_gpu_memory_gb}GB"),
            ("训练参数%",   f"{FULL_FINETUNE.actual_trainable_pct}%",  f"{LORA.actual_trainable_pct}%",    f"{QLORA.actual_trainable_pct}%"),
            ("效果评分",    f"{FULL_FINETUNE.performance_score}/10",    f"{LORA.performance_score}/10",     f"{QLORA.performance_score}/10"),
            ("稳定性",      f"{FULL_FINETUNE.stability_score}/10",      f"{LORA.stability_score}/10",       f"{QLORA.stability_score}/10"),
            ("训练速度",    FULL_FINETUNE.training_speed,               LORA.training_speed,               QLORA.training_speed),
            ("推理开销",    FULL_FINETUNE.inference_overhead,            LORA.inference_overhead,            QLORA.inference_overhead),
            ("最适合",      ", ".join(FULL_FINETUNE.best_for[:2]),      ", ".join(LORA.best_for[:2]),       ", ".join(QLORA.best_for[:2])),
            ("应避免",      ", ".join(FULL_FINETUNE.avoid_when[:2]),    ", ".join(LORA.avoid_when[:2]),     ", ".join(QLORA.avoid_when[:2])),
        ]

        for row in rows:
            print(f"| {row[0]:<14} | {row[1]:<{col_width}} | {row[2]:<{col_width}} | {row[3]:<{col_width}} |")

        print("=" * 80)

        # 一句话总结
        print("\n一句话总结：")
        for method in ALL_METHODS:
            print(f"  🔹 {method.full_name}: {method.summary}")

    def print_recommendation(self, scenario: UserScenario):
        """打印推荐结果"""
        rec = self.recommend(scenario)

        method_map = {"Full": FULL_FINETUNE, "LoRA": LORA, "QLoRA": QLORA}
        chosen = method_map[rec.method]

        print("\n" + "=" * 60)
        print(f"📋 你的场景 → 推荐结果")
        print("=" * 60)
        print(f"  GPU显存:    {scenario.gpu_memory_gb}GB")
        print(f"  数据量:     {scenario.data_size}")
        print(f"  任务类型:   {scenario.task_type}")
        print(f"  极致效果:   {'是' if scenario.need_max_performance else '否'}")
        print(f"  预算有限:   {'是' if scenario.budget_limited else '否'}")
        print(f"  模型大小:   {scenario.model_size}")
        print("-" * 60)
        print(f"  ✅ 推荐:    {chosen.full_name}")
        print(f"  📊 置信度:  {rec.confidence:.0%}")
        print(f"  📝 原因:    {rec.reason}")
        if rec.warnings:
            print(f"  ⚠️  警告:")
            for w in rec.warnings:
                print(f"      {w}")
        if rec.alternatives:
            alt_names = ", ".join(
                method_map[a].full_name for a in rec.alternatives
            )
            print(f"  🔄 备选:    {alt_names}")
        print("=" * 60)


# ========== 主程序：5个典型场景测试 ==========

def main():
    engine = DecisionEngine()

    # 先打印全景对比表
    engine.print_comparison_table()

    # 5个典型场景测试
    scenarios = [
        ("场景1: 个人开发者(RTX3060 12GB, 调语气风格)",
         UserScenario(gpu_memory_gb=12, data_size="small(<1k)", task_type="style",
                      need_max_performance=False, budget_limited=True, model_size="medium(1B-7B)")),
        ("场景2: 企业团队(A100 80GB, 深度领域适配)",
         UserScenario(gpu_memory_gb=80, data_size="medium(1k-10k)", task_type="domain",
                      need_max_performance=True, budget_limited=False, model_size="large(7B-70B)")),
        ("场景3: 学生笔记本(RTX4060 8GB, 快速验证想法)",
         UserScenario(gpu_memory_gb=8, data_size="small(<1k)", task_type="task",
                      need_max_performance=False, budget_limited=True, model_size="medium(1B-7B)")),
        ("场景4: 极端受限(仅4GB显存, 微调7B模型)",
         UserScenario(gpu_memory_gb=4, data_size="small(<1k)", task_type="domain",
                      need_max_performance=False, budget_limited=True, model_size="medium(1B-7B)")),
        ("场景5: 大厂集群(4×H100 320GB, 任务类型转换)",
         UserScenario(gpu_memory_gb=320, data_size="large(>10k)", task_type="task",
                      need_max_performance=True, budget_limited=False, model_size="huge(>70B)")),
    ]

    print("\n" + "🔬 五大典型场景测试" + "\n")

    for label, scenario in scenarios:
        print(f"\n{label}")
        engine.print_recommendation(scenario)


if __name__ == "__main__":
    main()
