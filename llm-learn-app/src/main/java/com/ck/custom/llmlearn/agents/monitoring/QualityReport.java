package com.ck.custom.llmlearn.agents.monitoring;

import java.util.List;
import java.util.Map;

/**
 * Agent质量评估报告
 *
 * 数据模型：
 * | 字段 | 含义 |
 * |------|------|
 * | totalScore | 总分（0-100），5维度加权平均 |
 * | grade | 评级（A/B/C/D/F） |
 * | dimensionScores | 各维度原始分（0-1） |
 * | suggestions | 优化建议（基于得分最低维度） |
 *
 * 评级标准：
 * A(≥90) → 生产可用，无需优化
 * B(70-89) → 可用，有优化空间
 * C(50-69) → 基本可用，需改进
 * D(30-49) → 不推荐上线
 * F(<30) → 严重问题
 */
public record QualityReport(
        double totalScore,
        String grade,
        Map<String, Double> dimensionScores,
        List<String> suggestions
) {

    /**
     * 生成文本格式的质量报告
     */
    public String toTextReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=" .repeat(60)).append("\n");
        sb.append("📋 Agent质量评估报告\n");
        sb.append("=" .repeat(60)).append("\n\n");

        sb.append(String.format("  总分: %.1f / 100 | 评级: %s\n", totalScore, grade));
        sb.append("\n");

        sb.append("📊 各维度得分:\n");
        sb.append("-".repeat(60)).append("\n");
        for (Map.Entry<String, Double> entry : dimensionScores.entrySet()) {
            String bar = scoreBar(entry.getValue());
            sb.append(String.format("  %s: %.2f %s\n", entry.getKey(), entry.getValue(), bar));
        }
        sb.append("\n");

        sb.append("💡 优化建议:\n");
        sb.append("-".repeat(60)).append("\n");
        for (String suggestion : suggestions) {
            sb.append("  ").append(suggestion).append("\n");
        }
        sb.append("=" .repeat(60)).append("\n");

        return sb.toString();
    }

    /**
     * 用字符画一个简易的得分条形图
     */
    private String scoreBar(double score) {
        int filled = (int) Math.round(score * 10);
        int empty = 10 - filled;
        return "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, empty))
                + " (" + (int)(score * 100) + "%)";
    }

    /**
     * 简要摘要（一行描述）
     */
    public String summary() {
        return String.format("质量评分: %.1f(%s) | 完整性:%.0f%% | 工具成功率:%.0f%% | 效率:%.0f%%",
                totalScore, grade,
                dimensionScores.getOrDefault("完整性(completeness)", 0.0) * 100,
                dimensionScores.getOrDefault("工具成功率(toolSuccessRate)", 0.0) * 100,
                dimensionScores.getOrDefault("效率(efficiency)", 0.0) * 100);
    }
}