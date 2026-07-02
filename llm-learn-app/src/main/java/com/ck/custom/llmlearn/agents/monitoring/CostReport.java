package com.ck.custom.llmlearn.agents.monitoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent成本分析报告
 *
 * 数据模型：
 * | 字段 | 含义 |
 * |------|------|
 * | totalTokens | 总Token消耗 |
 * | estimatedCostYuan | 估算费用（元） |
 * | totalDurationMs | 总耗时（毫秒） |
 * | toolDurationMap | 各工具耗时（毫秒） |
 * | toolCallCountMap | 各工具调用次数 |
 * | toolSuccessRateMap | 各工具成功率 |
 * | stepTimings | 每步耗时分级 |
 * | bottleneck | 最耗时步骤（瓶颈） |
 * | durationRatioMap | 各工具时间占比 |
 * | pricePerThousandTokens | Token单价 |
 */
public record CostReport(
        int totalTokens,
        double estimatedCostYuan,
        long totalDurationMs,
        Map<String, Long> toolDurationMap,
        Map<String, Integer> toolCallCountMap,
        Map<String, Double> toolSuccessRateMap,
        List<CostAnalyzer.StepTiming> stepTimings,
        CostAnalyzer.StepTiming bottleneck,
        Map<String, Double> durationRatioMap,
        double pricePerThousandTokens
) {

    /**
     * 生成文本格式的成本分析报告
     */
    public String toTextReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=" .repeat(60)).append("\n");
        sb.append("💰 Agent成本分析报告\n");
        sb.append("=" .repeat(60)).append("\n\n");

        // ===== 1. Token成本 =====
        sb.append("💵 Token成本:\n");
        sb.append("-".repeat(60)).append("\n");
        sb.append(String.format("  总Token消耗: %d\n", totalTokens));
        sb.append(String.format("  估算费用: ¥%.4f (单价: ¥%.4f/千Token)\n",
                estimatedCostYuan, pricePerThousandTokens));
        sb.append(String.format("  单次Agent平均Token: %d\n",
                totalTokens > 0 ? totalTokens : 0));
        sb.append("\n");

        // ===== 2. 时间成本 =====
        sb.append("⏱️ 时间成本:\n");
        sb.append("-".repeat(60)).append("\n");
        sb.append(String.format("  总耗时: %dms (%.1fs)\n", totalDurationMs, totalDurationMs / 1000.0));
        sb.append("\n");

        // ===== 3. 工具调用详情 =====
        sb.append("🔧 工具调用详情:\n");
        sb.append("-".repeat(60)).append("\n");
        if (toolCallCountMap.isEmpty()) {
            sb.append("  无工具调用（纯对话）\n");
        } else {
            sb.append(String.format("  %-12s | 调用次数 | 总耗时 | 时间占比 | 成功率\n", "工具名"));
            sb.append("  " + "-".repeat(52) + "\n");
            for (Map.Entry<String, Integer> entry : toolCallCountMap.entrySet()) {
                String toolName = entry.getKey();
                int callCount = entry.getValue();
                long duration = toolDurationMap.getOrDefault(toolName, 0L);
                double ratio = durationRatioMap.getOrDefault(toolName, 0.0);
                double successRate = toolSuccessRateMap.getOrDefault(toolName, 1.0);
                sb.append(String.format("  %-12s | %d次     | %dms   | %.0f%%     | %.0f%%\n",
                        toolName, callCount, duration, ratio * 100, successRate * 100));
            }
        }
        sb.append("\n");

        // ===== 4. 瓶颈分析 =====
        sb.append("🔍 瓶颈分析:\n");
        sb.append("-".repeat(60)).append("\n");
        if (bottleneck != null) {
            sb.append(String.format("  最耗时步骤: %s (%s)\n", bottleneck.name(), bottleneck.type()));
            sb.append(String.format("  耗时: %dms (%.1fs) | 速度评级: %s\n",
                    bottleneck.durationMs(), bottleneck.durationMs() / 1000.0, bottleneck.speedLevel()));
            sb.append(String.format("  占总耗时: %.0f%%\n",
                    totalDurationMs > 0 ? (double) bottleneck.durationMs() / totalDurationMs * 100 : 0));
        } else {
            sb.append("  无耗时数据\n");
        }
        sb.append("\n");

        // ===== 5. 每步耗时分级 =====
        sb.append("🚦 每步耗时分级:\n");
        sb.append("-".repeat(60)).append("\n");
        if (stepTimings.isEmpty()) {
            sb.append("  无耗时数据\n");
        } else {
            for (CostAnalyzer.StepTiming step : stepTimings) {
                sb.append(String.format("  %s [%s] %dms (%.1fs) → %s\n",
                        step.name(), step.type(), step.durationMs(),
                        step.durationMs() / 1000.0, step.speedLevel()));
            }
        }
        sb.append("\n");

        // ===== 6. 优化建议 =====
        sb.append("💡 成本优化建议:\n");
        sb.append("-".repeat(60)).append("\n");
        List<String> suggestions = generateCostSuggestions();
        for (String suggestion : suggestions) {
            sb.append("  ").append(suggestion).append("\n");
        }

        sb.append("=" .repeat(60)).append("\n");
        return sb.toString();
    }

    /**
     * 简要摘要
     */
    public String summary() {
        return String.format("成本: ¥%.4f(%dToken) | 耗时: %.1fs | 瓶颈: %s",
                estimatedCostYuan, totalTokens,
                totalDurationMs / 1000.0,
                bottleneck != null ? bottleneck.name() : "无");
    }

    /**
     * 生成成本优化建议
     */
    private List<String> generateCostSuggestions() {
        List<String> suggestions = new ArrayList<>();

        // Token成本建议
        if (totalTokens > 5000) {
            suggestions.add("⚠️ Token消耗较高(" + totalTokens + ")。优化方向：缩短SystemMessage、减少工具描述长度、用更短的输出格式。");
        } else if (totalTokens > 2000) {
            suggestions.add("💡 Token消耗中等。如需降低成本，可考虑使用更便宜的小模型处理简单任务。");
        }

        // 耗时建议
        if (totalDurationMs > 30000) {
            suggestions.add("⚠️ 总耗时>30秒，用户体验差。优先优化瓶颈步骤。");
        }

        // 瓶颈建议
        if (bottleneck != null && bottleneck.durationMs() > 10000) {
            String toolName = bottleneck.name().contains("/")
                    ? bottleneck.name().substring(bottleneck.name().indexOf('/') + 1)
                    : bottleneck.name();
            suggestions.add("⚠️ 瓶颈是" + toolName + "(" + bottleneck.durationMs() + "ms)。优化方向：增加超时限制、缓存结果、并行调用。");
        }

        // 工具成功率低
        for (Map.Entry<String, Double> entry : toolSuccessRateMap.entrySet()) {
            if (entry.getValue() < 0.5) {
                suggestions.add("⚠️ " + entry.getKey() + "成功率仅" + (int)(entry.getValue() * 100) + "%。每次失败都浪费Token和等待时间。");
            }
        }

        if (suggestions.isEmpty()) {
            suggestions.add("✅ 成本表现良好，无需特别优化。");
        }

        return suggestions;
    }
}
