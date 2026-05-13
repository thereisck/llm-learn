package com.ck.custom.llmlearn.prompt_optimizer.engine;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 *  * 质量评分（多维度分析）
 *  *
 *  * 核心维度：
 *  * - 准确性：事实是否正确
 *  * - 流畅性：语言是否自然
 *  * - 一致性：逻辑是否连贯
 *  * - 简洁性：是否冗余
 *  * - 专业性：术语是否准确
 * @author changkong
 * @date 2026/4/30 15:56
 **/
@Data
public class QualityScore {

    private Map<String, Double> dimensionScores;
    private double overallScore;
    private String bestDimension;
    private String worstDimension;

    // 维度定义
    public static final String ACCURACY = "准确性";
    public static final String FLUENCY = "流畅性";
    public static final String CONSISTENCY = "一致性";
    public static final String CONCISENESS = "简洁性";
    public static final String PROFESSIONALISM = "专业性";

    // ========== 构造函数 ==========

    public QualityScore() {
        this.dimensionScores = new HashMap<>();
        // 初始化所有维度为0
        dimensionScores.put(ACCURACY, 0.0);
        dimensionScores.put(FLUENCY, 0.0);
        dimensionScores.put(CONSISTENCY, 0.0);
        dimensionScores.put(CONCISENESS, 0.0);
        dimensionScores.put(PROFESSIONALISM, 0.0);
    }

    // ========== 核心方法 ==========

    /**
     * 设置维度评分
     */
    public QualityScore setDimension(String dimension, double score) {
        if (!dimensionScores.containsKey(dimension)) {
            throw new IllegalArgumentException("未知维度: " + dimension);
        }
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("评分范围：0-100");
        }

        dimensionScores.put(dimension, score);
        calculateOverall();
        return this;
    }

    /**
     * 获取维度评分
     */
    public double getDimension(String dimension) {
        return dimensionScores.getOrDefault(dimension, 0.0);
    }

    /**
     * 计算综合评分（平均值）
     */
    private void calculateOverall() {
        double sum = dimensionScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
        this.overallScore = sum / dimensionScores.size();

        // 找出最高和最低维度
        this.bestDimension = dimensionScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("无");

        this.worstDimension = dimensionScores.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("无");
    }

    /**
     * 获取改进建议
     */
    public String getImprovementSuggestion() {
        if (worstDimension.equals("无")) {
            return "无需改进";
        }

        double worstScore = getDimension(worstDimension);

        if (worstScore < 60) {
            return String.format("重点改进：%s（当前评分 %.2f，低于60分）", worstDimension, worstScore);
        } else if (worstScore < 80) {
            return String.format("可优化：%s（当前评分 %.2f，建议提升到80分以上）", worstDimension, worstScore);
        } else {
            return "质量良好，继续保持";
        }
    }
}
