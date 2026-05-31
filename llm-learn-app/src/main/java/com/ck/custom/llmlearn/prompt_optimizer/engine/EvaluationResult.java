package com.ck.custom.llmlearn.prompt_optimizer.engine;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 *  * 评估结果
 *  *
 *  * 核心字段：
 *  * - accuracyScore: 准确性评分（0-100）
 *  * - completenessScore: 完整度评分（0-100）
 *  * - formatScore: 格式一致性评分（0-100）
 *  * - overallScore: 综合评分（加权平均）
 *  * - issues: 发现的问题列表
 * @author changkong
 * @date 2026/4/30 15:53
 **/
@Data
public class EvaluationResult {
    private String responseId;
    private double accuracyScore;
    private double completenessScore;
    private double formatScore;
    private double overallScore;
    private List<String> issues;
    private List<String> strengths;
    private String recommendation;

    // 权重配置
    private static final double ACCURACY_WEIGHT = 0.4;
    private static final double COMPLETENESS_WEIGHT = 0.3;
    private static final double FORMAT_WEIGHT = 0.3;

    public EvaluationResult() {
        this.issues = new ArrayList<>();
        this.strengths = new ArrayList<>();
    }

    public EvaluationResult(String responseId, double accuracy, double completeness, double format) {
        this();
        this.responseId = responseId;
        this.accuracyScore = accuracy;
        this.completenessScore = completeness;
        this.formatScore = format;
        calculateOverallScore();
    }

    /**
     * 添加问题
     */
    public EvaluationResult addIssue(String issue) {
        this.issues.add(issue);
        return this;
    }

    /**
     * 添加优点
     */
    public EvaluationResult addStrength(String strength) {
        this.strengths.add(strength);
        return this;
    }

    /**
     * 获取评分等级
     */
    public String getGrade() {
        if (overallScore >= 90) return "优秀(A)";
        if (overallScore >= 80) return "良好(B)";
        if (overallScore >= 70) return "中等(C)";
        if (overallScore >= 60) return "及格(D)";
        return "不及格(F)";
    }

    /**
     * 是否合格
     */
    public boolean isPassing() {
        return overallScore >= 60;
    }

    /**
     * 计算综合评分（加权平均）
     */
    public void calculateOverallScore() {
        this.overallScore =
                accuracyScore * ACCURACY_WEIGHT +
                        completenessScore * COMPLETENESS_WEIGHT +
                        formatScore * FORMAT_WEIGHT;
    }
}
