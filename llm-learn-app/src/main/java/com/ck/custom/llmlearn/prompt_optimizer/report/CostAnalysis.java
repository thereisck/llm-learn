package com.ck.custom.llmlearn.prompt_optimizer.report;

import com.ck.custom.llmlearn.prompt_optimizer.client.TokenUsage;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 *  * - totalTokens: 总Token消耗
 *  * - totalCostUSD: 总成本（美元）
 *  * - totalCostCNY: 总成本（人民币）
 *  * - cheapestIndex: 最便宜的方案索引
 *  * - mostExpensiveIndex: 最贵的方案索引
 * @author changkong
 * @date 2026/4/30 16:13
 **/
@Getter
public class CostAnalysis {
    private List<TokenUsage> usages;
    private int totalInputTokens;
    private int totalOutputTokens;
    private int totalTokens;
    private double totalCostUSD;
    private double totalCostCNY;
    private int cheapestIndex;
    private int mostExpensiveIndex;

    // ========== 构造函数 ==========

    public CostAnalysis() {
        this.usages = new ArrayList<>();
    }

    public CostAnalysis(List<TokenUsage> usages) {
        this.usages = usages;
        calculateTotals();
        findCheapestAndMostExpensive();
    }

    // ========== 核心方法 ==========

    private void calculateTotals() {
        for (TokenUsage usage : usages) {
            totalInputTokens += usage.getInputTokens();
            totalOutputTokens += usage.getOutputTokens();
            totalCostUSD += usage.calculateCostUSD();
        }
        totalTokens = totalInputTokens + totalOutputTokens;
        totalCostCNY = totalCostUSD * 7.2;
    }

    private void findCheapestAndMostExpensive() {
        if (usages.isEmpty()) return;

        double minCost = Double.MAX_VALUE;
        double maxCost = Double.MIN_VALUE;

        for (int i = 0; i < usages.size(); i++) {
            double cost = usages.get(i).calculateCostUSD();
            if (cost < minCost) {
                minCost = cost;
                cheapestIndex = i;
            }
            if (cost > maxCost) {
                maxCost = cost;
                mostExpensiveIndex = i;
            }
        }
    }

    /**
     * 获取成本对比摘要
     */
    public String getSummary() {
        if (usages.isEmpty()) {
            return "无Token消耗数据";
        }

        return String.format(
                "总消耗：%d tokens（输入%d + 输出%d），总成本：$%.4f（≈¥%.3f）",
                totalTokens, totalInputTokens, totalOutputTokens,
                totalCostUSD, totalCostCNY
        );
    }

    /**
     * 获取方案成本对比表（Markdown格式）
     */
    public String getComparisonTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("| 方案 | 输入Token | 输出Token | 总Token | 成本(USD) | 成本(CNY) |\n");
        sb.append("|------|-----------|-----------|---------|-----------|----------|\n");

        for (int i = 0; i < usages.size(); i++) {
            TokenUsage usage = usages.get(i);
            String marker = (i == cheapestIndex) ? "⭐ " : "";
            sb.append(String.format("| %s方案%d | %d | %d | %d | $%.4f | ¥%.3f |\n",
                    marker, i + 1,
                    usage.getInputTokens(),
                    usage.getOutputTokens(),
                    usage.getTotalTokens(),
                    usage.calculateCostUSD(),
                    usage.calculateCostCNY()
            ));
        }

        return sb.toString();
    }
}
