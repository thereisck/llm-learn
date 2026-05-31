package com.ck.custom.llmlearn.prompt_optimizer.client;

import lombok.Data;

/**
 *  * Token消耗统计
 *  *
 *  * 核心字段：
 *  * - inputTokens: 输入Prompt的Token数
 *  * - outputTokens: 输出Response的Token数
 *  * - totalTokens: 总Token数
 * @author changkong
 * @date 2026/4/30 15:07
 **/
@Data
public class TokenUsage {

    private int inputTokens;
    private int outputTokens;
    private int totalTokens;

    // ========== 构造函数 ==========

    public TokenUsage() {
        this.totalTokens = 0;
    }

    public TokenUsage(int inputTokens, int outputTokens) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = inputTokens + outputTokens;
    }

    // ========== 静态工厂方法 ==========

    public static TokenUsage of(int inputTokens, int outputTokens) {
        return new TokenUsage(inputTokens, outputTokens);
    }

    // ========== 辅助方法 ==========

    private void updateTotal() {
        this.totalTokens = this.inputTokens + this.outputTokens;
    }

    /**
     * 计算成本（美元）
     *
     * 按GPT-3.5-turbo价格：
     * - Input: $0.0015 / 1K tokens
     * - Output: $0.002 / 1K tokens
     */
    public double calculateCostUSD() {
        double inputCost = inputTokens * 0.0015 / 1000;
        double outputCost = outputTokens * 0.002 / 1000;
        return inputCost + outputCost;
    }

    /**
     * 计算成本（人民币，汇率约7.2）
     */
    public double calculateCostCNY() {
        return calculateCostUSD() * 7.2;
    }
}
