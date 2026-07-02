"""
微调核心机制模拟 - Week7 Day1 第3步
用纯numpy模拟 LoRA/QLoRA/全量微调的核心数学原理

不依赖GPU，本地跑通，理解底层逻辑
"""

import numpy as np
from dataclasses import dataclass
from typing import Optional


# ========== LoRA核心：低秩分解 ==========

@dataclass
class LoRAAdapter:
    """
    一个LoRA适配器的核心数据结构
    
    原理：W ∈ R^{d×d} 的更新量 ΔW ≈ A × B
    其中 A ∈ R^{d×r}, B ∈ R^{r×d}, r << d
    
    可训练参数：d×r + r×d = 2dr（对比全量 d²）
    当 r=4, d=4096 → 2×4096×4 = 32,768 vs 全量 16,777,216
    参数占比：0.2%！
    """
    name: str                    # 适配器名称
    rank: int                    # 低秩维度 r
    d: int                       # 原始权重维度 d
    alpha: float                 # 缩放因子（通常=rank）
    A: np.ndarray = None         # 下投影矩阵 A ∈ R^{d×r}
    B: np.ndarray = None         # 上投影矩阵 B ∈ R^{r×d}

    def __post_init__(self):
        """初始化LoRA矩阵——这是LoRA的关键设计"""
        if self.A is None:
            # A 用 Kaiming初始化（正态分布）——保证训练开始时有合理梯度
            self.A = np.random.randn(self.d, self.rank) * 0.01
        if self.B is None:
            # B 用零初始化——保证 ΔW = A×B = 0，训练开始时没有扰动！
            # 这是LoRA的精妙之处：不破坏原始权重，从零开始学增量
            self.B = np.zeros((self.rank, self.d))

    @property
    def trainable_params(self) -> int:
        """可训练参数量"""
        return self.d * self.rank + self.rank * self.d

    @property
    def full_params(self) -> int:
        """全量微调对应参数量"""
        return self.d * self.d

    @property
    def params_ratio(self) -> float:
        """参数占比"""
        return self.trainable_params / self.full_params

    @property
    def delta_W(self) -> np.ndarray:
        """计算权重更新量 ΔW = (alpha/rank) × A × B"""
        scale = self.alpha / self.rank
        return scale * (self.A @ self.B)

    def apply_to(self, W: np.ndarray) -> np.ndarray:
        """应用LoRA到原始权重：W' = W + ΔW"""
        return W + self.delta_W

    def merge_into(self, W: np.ndarray) -> np.ndarray:
        """合并LoRA到原始权重——合并后推理零开销！
        W_merged = W + ΔW，之后不再需要A和B
        """
        merged = W + self.delta_W
        return merged

    def simulate_training_step(self, learning_rate: float = 0.01):
        """模拟一步训练：用随机梯度更新A和B"""
        # 模拟梯度（实际训练中由loss反向传播计算）
        grad_A = np.random.randn(self.d, self.rank) * 0.1
        grad_B = np.random.randn(self.rank, self.d) * 0.1

        # SGD更新
        self.A -= learning_rate * grad_A
        self.B -= learning_rate * grad_B


# ========== QLoRA核心：4bit量化 ==========

@dataclass
class QuantizedWeight:
    """
    QLoRA的4bit量化核心
    
    原理：FP16权重 W → NF4量化 → 存储4bit
    训练时：4bit → 反量化回FP16 → 计算梯度 → 只更新LoRA部分
    推理时：可以4bit直接推理，或反量化+合并LoRA
    
    NF4（NormalFloat4）：专门为正态分布权重设计的4bit编码
    量化公式：q = round(W / absmax)，absmax是每组权重的最大绝对值
    """
    original: np.ndarray          # 原始FP16权重
    group_size: int = 64          # 分组量化大小（QLoRA默认64）
    absmax: np.ndarray = None     # 每组的最大绝对值
    quantized: np.ndarray = None  # 量化后的4bit值（用int8模拟）
    zero_point: np.ndarray = None # 每组的零点偏移

    def __post_init__(self):
        """执行量化"""
        self._quantize()

    def _quantize(self):
        """分组量化：FP16 → 4bit"""
        n_groups = self.original.shape[0] // self.group_size
        self.absmax = np.zeros(n_groups)
        self.quantized = np.zeros_like(self.original, dtype=np.int8)
        self.zero_point = np.zeros(n_groups)

        for i in range(n_groups):
            start = i * self.group_size
            end = start + self.group_size
            group = self.original[start:end]

            # 计算组的absmax和零点
            self.absmax[i] = np.max(np.abs(group))
            self.zero_point[i] = np.mean(group)  # 简化：用均值作为零点

            # 量化到4bit范围[-8, 7]（用int8存储，实际只占4bit）
            scale = self.absmax[i] / 7.0 if self.absmax[i] > 0 else 1.0
            self.quantized[start:end] = np.clip(
                np.round((group - self.zero_point[i]) / scale),
                -8, 7
            ).astype(np.int8)

    def dequantize(self) -> np.ndarray:
        """反量化：4bit → FP16（训练时需要）"""
        result = np.zeros_like(self.original, dtype=np.float64)
        n_groups = self.original.shape[0] // self.group_size

        for i in range(n_groups):
            start = i * self.group_size
            end = start + self.group_size
            scale = self.absmax[i] / 7.0 if self.absmax[i] > 0 else 1.0

            # 反量化：value = quantized × scale + zero_point
            result[start:end] = self.quantized[start:end].astype(np.float64) * scale + self.zero_point[i]

        return result

    @property
    def compression_ratio(self) -> float:
        """压缩比：FP16(16bit) → NF4(4bit) = 4倍压缩"""
        return 16.0 / 4.0

    @property
    def memory_saved_gb(self) -> float:
        """节省的显存（假设参数量=original长度）"""
        original_bytes = self.original.shape[0] * 2  # FP16 = 2 bytes
        quantized_bytes = self.original.shape[0] * 0.5  # NF4 = 0.5 bytes
        saved = (original_bytes - quantized_bytes) / (1024 ** 3)
        return saved


# ========== 全量微调模拟 ==========

@dataclass
class FullFinetuneSimulator:
    """全量微调：直接更新所有权重"""
    W: np.ndarray                # 原始权重
    learning_rate: float = 0.01

    def training_step(self):
        """一步全量微调：更新所有d²个参数"""
        grad_W = np.random.randn(*self.W.shape) * 0.1
        self.W -= self.learning_rate * grad_W


# ========== 对比实验 ==========

def run_comparison_experiment():
    """
    核心对比实验：同一任务，三种微调方式
    模拟：7B模型的一个权重层（简化为 d=512）
    """
    print("=" * 70)
    print("🔬 LoRA / QLoRA / 全量微调 核心机制对比实验")
    print("=" * 70)

    d = 512  # 简化维度（真实7B模型d=4096）
    r = 4    # LoRA秩（典型值）

    # 生成原始权重（模拟预训练模型的权重分布）
    np.random.seed(42)
    W_original = np.random.randn(d, d) * 0.1  # 正态分布，小方差

    # 模拟输入
    x_input = np.random.randn(d)

    # 原始模型输出（baseline）
    y_original = W_original @ x_input

    print(f"\n📊 模型配置：")
    print(f"  权重维度 d = {d}（真实7B模型 d=4096）")
    print(f"  LoRA秩 r = {r}")
    print(f"  原始权重参数量 = {d*d} = {d*d:,}")
    print(f"  LoRA可训练参数 = 2×{d}×{r} = {2*d*r:,}")
    print(f"  LoRA参数占比 = {2*d*r/(d*d)*100:.2f}%")

    # ===== 1. LoRA实验 =====
    print("\n" + "-" * 70)
    print("🔹 实验1：LoRA（低秩分解）")
    print("-" * 70)

    lora = LoRAAdapter(name="style_adapter", rank=r, d=d, alpha=r)

    print(f"  初始 ΔW = A×B 的范数: {np.linalg.norm(lora.delta_W):.6f}")
    print(f"  （B初始化为零→ΔW=0→不破坏原始权重✅）")

    # 模拟10步训练
    for step in range(10):
        lora.simulate_training_step(learning_rate=0.01)

    W_lora = lora.apply_to(W_original)
    y_lora = W_lora @ x_input

    print(f"  训练10步后 ΔW 范数: {np.linalg.norm(lora.delta_W):.6f}")
    print(f"  输出变化量: {np.linalg.norm(y_lora - y_original):.6f}")
    print(f"  参数效率: 用 {lora.params_ratio*100:.2f}% 的参数达到效果变化")

    # 合并实验
    W_merged = lora.merge_into(W_original)
    print(f"\n  合并后验证:")
    print(f"  W_merged ≈ W + ΔW ✅")
    print(f"  合前后输出差异: {np.linalg.norm(W_merged @ x_input - y_lora):.10f}（≈0=零开销推理✅）")

    # ===== 2. 多适配器切换 =====
    print("\n" + "-" * 70)
    print("🔹 实验2：多LoRA适配器切换（同一基础模型，不同任务）")
    print("-" * 70)

    # 创建3个不同任务的适配器
    adapters = [
        LoRAAdapter(name="code_style", rank=r, d=d, alpha=r),
        LoRAAdapter(name="formal_style", rank=r, d=d, alpha=r),
        LoRAAdapter(name="casual_style", rank=r, d=d, alpha=r),
    ]

    # 每个适配器训练10步
    for adapter in adapters:
        for step in range(10):
            adapter.simulate_training_step(learning_rate=0.01)

    # 计算每个适配器的输出
    outputs = {}
    for adapter in adapters:
        W_adapted = adapter.apply_to(W_original)
        outputs[adapter.name] = W_adapted @ x_input

    # 适配器之间的差异
    print(f"  3个适配器输出对比:")
    for name, y in outputs.items():
        diff_from_original = np.linalg.norm(y - y_original)
        print(f"    {name}: 偏离原始输出 {diff_from_original:.4f}")

    # 适配器之间互不相同
    for i in range(len(adapters)):
        for j in range(i+1, len(adapters)):
            diff = np.linalg.norm(outputs[adapters[i].name] - outputs[adapters[j].name])
            print(f"    {adapters[i].name} vs {adapters[j].name}: 差异 {diff:.4f}")

    print(f"\n  💡 关键洞察：")
    print(f"    同一基础模型 + 不同适配器 → 不同行为")
    print(f"    切换成本=0（只换A/B矩阵，不用重新加载模型）")
    print(f"    3个适配器总参数 = {3 * 2*d*r:,} vs 全量3个模型 = {3*d*d:,}")
    print(f"    参数节省 = {(1 - 3*2*d*r/(3*d*d))*100:.1f}%")

    # ===== 3. QLoRA量化实验 =====
    print("\n" + "-" * 70)
    print("🔹 实验3：QLoRA（4bit量化 + LoRA）")
    print("-" * 70)

    # 对原始权重做4bit量化
    W_flat = W_original.flatten()
    quantized = QuantizedWeight(original=W_flat, group_size=64)

    # 反量化
    W_dequantized = quantized.dequantize().reshape(d, d)

    # 量化精度损失
    quantization_loss = np.linalg.norm(W_dequantized - W_original) / np.linalg.norm(W_original)
    print(f"  量化→反量化精度损失: {quantization_loss:.4f}（{quantization_loss*100:.2f}%）")
    print(f"  原始权重范数: {np.linalg.norm(W_original):.4f}")
    print(f"  反量化权重范数: {np.linalg.norm(W_dequantized):.4f}")
    print(f"  压缩比: FP16→NF4 = {quantized.compression_ratio:.0f}x")

    # QLoRA：反量化权重 + LoRA
    qlora = LoRAAdapter(name="qlora_adapter", rank=r, d=d, alpha=r)
    for step in range(10):
        qlora.simulate_training_step(learning_rate=0.01)

    W_qlora = qlora.apply_to(W_dequantized)
    y_qlora = W_qlora @ x_input

    print(f"\n  QLoRA输出偏离原始: {np.linalg.norm(y_qlora - y_original):.6f}")
    print(f"  QLoRA = 反量化精度损失 + LoRA增量调整")

    # ===== 4. 全量微调实验 =====
    print("\n" + "-" * 70)
    print("🔹 实验4：全量微调（直接更新全部权重）")
    print("-" * 70)

    full_ft = FullFinetuneSimulator(W=W_original.copy(), learning_rate=0.01)
    for step in range(10):
        full_ft.training_step()

    y_full = full_ft.W @ x_input
    print(f"  全量微调后输出偏离原始: {np.linalg.norm(y_full - y_original):.6f}")
    print(f"  更新参数量: {d*d:,}（全部）")

    # ===== 5. 三种方法总结对比 =====
    print("\n" + "=" * 70)
    print("📊 三种方法核心机制总结")
    print("=" * 70)

    results = {
        "全量微调": {
            "更新参数": d*d,
            "参数占比": "100%",
            "输出偏离": np.linalg.norm(y_full - y_original),
            "推理开销": "无",
            "切换成本": "重新加载整个模型",
        },
        "LoRA": {
            "更新参数": 2*d*r,
            "参数占比": f"{2*d*r/(d*d)*100:.2f}%",
            "输出偏离": np.linalg.norm(y_lora - y_original),
            "推理开销": "合并后≈0",
            "切换成本": "只换A/B矩阵",
        },
        "QLoRA": {
            "更新参数": 2*d*r,
            "参数占比": f"{2*d*r/(d*d)*100:.2f}%",
            "输出偏离": np.linalg.norm(y_qlora - y_original),
            "推理开销": "反量化开销",
            "切换成本": "只换A/B矩阵",
        },
    }

    for method, data in results.items():
        print(f"\n  {method}:")
        for key, value in data.items():
            print(f"    {key}: {value}")

    # ===== 6. LoRA核心数学直觉 =====
    print("\n" + "=" * 70)
    print("🧠 LoRA核心数学直觉")
    print("=" * 70)

    print("""
  问题：为什么低秩分解 ΔW ≈ A×B 能近似全量微调的效果？

  答案：预训练模型的权重更新天然是「低秩」的！

  直觉：
  - 预训练模型已经学好了通用能力（权重W是高秩的）
  - 微调只是做「小幅调整」（调整量ΔW是低秩的）
  - 类比：一幅画已经画好了90%，微调只是修细节→不需要重画整幅画

  数学证明：
  - A ∈ R^{d×r} 将d维投影到r维（信息压缩）
  - B ∈ R^{r×d} 将r维还原到d维（信息还原）
  - A×B 的秩最多为 r << d
  - 当 r=4, d=4096 → ΔW 只有4个独立的「调整方向」
  - 这4个方向足够覆盖微调需要的调整量

  为什么B初始化为零？
  - ΔW = A×B，B=0 → ΔW=0
  - 训练开始时不破坏原始权重
  - 从「零扰动」开始学增量，比随机初始化更稳定
    """)


if __name__ == "__main__":
    run_comparison_experiment()
