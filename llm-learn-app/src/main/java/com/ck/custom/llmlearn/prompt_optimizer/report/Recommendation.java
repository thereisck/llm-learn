package com.ck.custom.llmlearn.prompt_optimizer.report;

import lombok.Data;

/**
 *  * 推荐结果
 *  *
 *  * 核心字段：
 *  * - bestIndex: 最优方案索引
 *  * - score: 推荐评分
 *  * - reason: 推荐理由
 *  * - alternativeIndex: 备选方案索引
 * @author changkong
 * @date 2026/4/30 16:15
 **/
@Data
public class Recommendation {
    private int bestIndex;
    private double bestScore;
    private String reason;
    private int alternativeIndex;
    private String alternativeReason;

    // ========== 构造函数 ==========

    public Recommendation() {
    }

    public Recommendation(int bestIndex, double bestScore, String reason) {
        this.bestIndex = bestIndex;
        this.bestScore = bestScore;
        this.reason = reason;
    }

    // ========== 静态工厂方法 ==========

    /**
     * 创建推荐结果
     */
    public static Recommendation of(int bestIndex, double bestScore, String reason) {
        return new Recommendation(bestIndex, bestScore, reason);
    }

    /**
     * 创建带备选的推荐
     */
    public static Recommendation withAlternative(int bestIndex, double bestScore, String reason,
                                                 int altIndex, String altReason) {
        Recommendation rec = new Recommendation(bestIndex, bestScore, reason);
        rec.alternativeIndex = altIndex;
        rec.alternativeReason = altReason;
        return rec;
    }

    /**
     * 获取推荐文案
     */
    public String getRecommendationText() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("推荐方案%d（评分 %.2f），理由：%s\n",
                bestIndex + 1, bestScore, reason));

        if (alternativeReason != null) {
            sb.append(String.format("备选方案%d，理由：%s",
                    alternativeIndex + 1, alternativeReason));
        }

        return sb.toString();
    }
}
