package com.ck.custom.llmlearn.agents.monitoring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent质量评估器（规则评分版）
 *
 * 核心思路：从TraceListener的traces里提取5个可量化的质量维度，加权平均算总分
 *
 * 5个维度：
 * 1. 完整性(completeness) → Agent有没有给出最终输出？
 * 2. 工具成功率(toolSuccessRate) → 工具调用成功比例
 * 3. 效率(efficiency) → 完成任务用了多少步？
 * 4. 耗时合理性(durationScore) → 总耗时是否可接受？
 * 5. 错误率(errorRate) → 有没有报错？
 *
 * 数据全从TraceListener.traces里提取——上下游关系：
 * Agent执行 → TraceListener记录 → QualityEvaluator评分 → QualityReport
 */
public class AgentQualityEvaluator {

    // ========== 5个维度的权重 ==========
    private static final double W_COMPLETE = 0.30;
    private static final double W_TOOL_SUCCESS = 0.25;
    private static final double W_EFFICIENCY = 0.15;
    private static final double W_DURATION = 0.15;
    private static final double W_ERROR = 0.15;

    // ========== 耗时评分阈值（毫秒） ==========
    private static final long DURATION_EXCELLENT = 5000;
    private static final long DURATION_GOOD = 15000;
    private static final long DURATION_OK = 30000;

    // ========== 评级阈值 ==========
    private static final double GRADE_A = 90;
    private static final double GRADE_B = 70;
    private static final double GRADE_C = 50;
    private static final double GRADE_D = 30;

    /**
     * 评估一次Agent执行的质量
     *
     * @param traces TraceListener记录的所有轨迹事件
     * @param minSteps 理论最少步骤数（用于计算效率）
     * @return QualityReport 质量报告
     */
    public QualityReport evaluate(List<TraceListener.TraceRecord> traces, int minSteps) {
        double completeness = calcCompleteness(traces);
        double toolSuccessRate = calcToolSuccessRate(traces);
        double efficiency = calcEfficiency(traces, minSteps);
        double durationScore = calcDurationScore(traces);
        double errorRateScore = calcErrorRateScore(traces);

        double totalScore = (completeness * W_COMPLETE
                + toolSuccessRate * W_TOOL_SUCCESS
                + efficiency * W_EFFICIENCY
                + durationScore * W_DURATION
                + errorRateScore * W_ERROR) * 100;

        String grade = calcGrade(totalScore);

        List<String> suggestions = generateSuggestions(
                completeness, toolSuccessRate, efficiency, durationScore, errorRateScore);

        Map<String, Double> dimensionScores = new LinkedHashMap<>();
        dimensionScores.put("完整性(completeness)", completeness);
        dimensionScores.put("工具成功率(toolSuccessRate)", toolSuccessRate);
        dimensionScores.put("效率(efficiency)", efficiency);
        dimensionScores.put("耗时合理性(duration)", durationScore);
        dimensionScores.put("错误率(errorRate)", errorRateScore);

        return new QualityReport(totalScore, grade, dimensionScores, suggestions);
    }

    // ========== 维度1：完整性 ==========

    private double calcCompleteness(List<TraceListener.TraceRecord> traces) {
        TraceListener.TraceRecord lastEnd = null;
        for (TraceListener.TraceRecord trace : traces) {
            if ("agent_end".equals(trace.type())) {
                lastEnd = trace;
            }
        }

        if (lastEnd == null) return 0.0;

        String output = lastEnd.content();
        if (output == null || output.isEmpty() || "null".equals(output)) return 0.5;

        return 1.0;
    }

    // ========== 维度2：工具成功率 ==========

    private double calcToolSuccessRate(List<TraceListener.TraceRecord> traces) {
        List<TraceListener.TraceRecord> toolEnds = traces.stream()
                .filter(t -> "tool_end".equals(t.type()))
                .toList();

        if (toolEnds.isEmpty()) return 1.0;

        int successCount = 0;
        for (TraceListener.TraceRecord toolEnd : toolEnds) {
            String result = toolEnd.content();
            if (result != null && !result.contains("❌") && !result.contains("失败")) {
                successCount++;
            }
        }

        return (double) successCount / toolEnds.size();
    }

    // ========== 维度3：效率 ==========

    private double calcEfficiency(List<TraceListener.TraceRecord> traces, int minSteps) {
        int actualSteps = traces.size();

        if (minSteps <= 0) return 1.0;
        if (actualSteps <= minSteps) return 1.0;

        return Math.max(0.1, (double) minSteps / actualSteps);
    }

    // ========== 维度4：耗时合理性 ==========

    private double calcDurationScore(List<TraceListener.TraceRecord> traces) {
        long totalDuration = traces.stream()
                .filter(t -> t.durationMs() >= 0)
                .mapToLong(TraceListener.TraceRecord::durationMs)
                .sum();

        if (totalDuration == 0) return 1.0;

        if (totalDuration < DURATION_EXCELLENT) return 1.0;
        else if (totalDuration < DURATION_GOOD) return 0.7;
        else if (totalDuration < DURATION_OK) return 0.5;
        else return 0.3;
    }

    // ========== 维度5：错误率 ==========

    private double calcErrorRateScore(List<TraceListener.TraceRecord> traces) {
        int agentStarts = 0;
        int agentErrors = 0;

        for (TraceListener.TraceRecord trace : traces) {
            if ("agent_start".equals(trace.type())) agentStarts++;
            if ("agent_error".equals(trace.type())) agentErrors++;
        }

        if (agentStarts == 0) return 1.0;
        if (agentErrors == 0) return 1.0;
        if (agentErrors == 1) return 0.5;

        return Math.max(0.0, 1.0 - (double) agentErrors / agentStarts);
    }

    // ========== 评级计算 ==========

    private String calcGrade(double totalScore) {
        if (totalScore >= GRADE_A) return "A";
        if (totalScore >= GRADE_B) return "B";
        if (totalScore >= GRADE_C) return "C";
        if (totalScore >= GRADE_D) return "D";
        return "F";
    }

    // ========== 优化建议生成 ==========

    private List<String> generateSuggestions(
            double completeness, double toolSuccessRate,
            double efficiency, double durationScore, double errorRateScore) {

        List<String> suggestions = new ArrayList<>();

        if (completeness < 0.5) {
            suggestions.add("⚠️ 完整性低：Agent经常无法完成输出。检查：是否超时？是否进入死循环？是否缺少关键工具？");
        } else if (completeness < 1.0) {
            suggestions.add("💡 完整性一般：部分调用输出为空。增加maxIterations限制，防止无限循环。");
        }

        if (toolSuccessRate < 0.5) {
            suggestions.add("⚠️ 工具成功率低：超过一半的工具调用失败。检查：API连接是否稳定？超时设置是否合理？参数传递是否正确？");
        } else if (toolSuccessRate < 0.8) {
            suggestions.add("💡 工具成功率一般：建议增加重试机制或fallback策略。");
        }

        if (efficiency < 0.3) {
            suggestions.add("⚠️ 效率低：实际步骤远超理论最少步骤。检查：是否重复调用同一工具？是否LLM在犹豫不决？");
        } else if (efficiency < 0.6) {
            suggestions.add("💡 效率一般：考虑优化SystemMessage，让LLM更果断地选择工具。");
        }

        if (durationScore < 0.5) {
            suggestions.add("⚠️ 耗时过长：总耗时>30秒。优化方向：换更快的模型、减少工具调用轮次、增加缓存。");
        } else if (durationScore < 0.7) {
            suggestions.add("💡 耗时偏慢：15-30秒完成。如需提速，可考虑并行调用多个工具。");
        }

        if (errorRateScore < 0.5) {
            suggestions.add("⚠️ 错误率高：Agent执行频繁出错。必须增加错误处理：try-catch、重试、fallback。");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("✅ 各维度表现良好，无需特别优化。");
        }

        return suggestions;
    }
}