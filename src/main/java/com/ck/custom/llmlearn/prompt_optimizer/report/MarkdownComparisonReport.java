package com.ck.custom.llmlearn.prompt_optimizer.report;

import com.ck.custom.llmlearn.prompt_optimizer.client.TokenUsage;
import com.ck.custom.llmlearn.prompt_optimizer.engine.EvaluationResult;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * * Markdown对比报告生成器
 *  *
 *  * 输出格式：Markdown表格 + 成本分析 + 推荐
 * @author changkong
 * @date 2026/4/30 16:16
 **/
@Component
public class MarkdownComparisonReport implements ComparisonReport {
    @Override
    public String generateMarkdown(List<EvaluationResult> results) {
        if (results == null || results.isEmpty()) {
            return "# 对比报告\n\n无评估数据。\n";
        }

        StringBuilder sb = new StringBuilder();

        // 标题
        sb.append("# Prompt效果对比报告\n\n");
        sb.append(String.format("生成时间：%s\n\n", new Date()));

        // 对比表格
        sb.append("## 效果对比表\n\n");
        sb.append(generateComparisonTable(results));

        // 详细分析
        sb.append("\n## 详细分析\n\n");
        for (int i = 0; i < results.size(); i++) {
            EvaluationResult result = results.get(i);
            sb.append(String.format("### 方案%d\n\n", i + 1));
            sb.append(generateResultDetail(result));
        }

        return sb.toString();
    }

    @Override
    public CostAnalysis analyzeCost(List<TokenUsage> usages) {
        return new CostAnalysis(usages);
    }

    @Override
    public Recommendation recommendBest(List<EvaluationResult> results) {
        if (results == null || results.isEmpty()) {
            return new Recommendation(0, 0, "无评估数据");
        }

        // 按综合评分排序
        List<EvaluationResult> sorted = results.stream()
                .sorted((r1, r2) -> Double.compare(r2.getOverallScore(), r1.getOverallScore()))
                .collect(Collectors.toList());

        EvaluationResult best = sorted.get(0);
        int bestIndex = results.indexOf(best);

        // 生成推荐理由
        String reason = generateRecommendationReason(best);

        // 如果有第二名，作为备选
        if (sorted.size() > 1) {
            EvaluationResult alt = sorted.get(1);
            int altIndex = results.indexOf(alt);
            String altReason = String.format("评分 %.2f，可作为备选", alt.getOverallScore());

            return Recommendation.withAlternative(bestIndex, best.getOverallScore(), reason,
                    altIndex, altReason);
        }

        return Recommendation.of(bestIndex, best.getOverallScore(), reason);
    }

    @Override
    public String exportFullReport(List<EvaluationResult> results, List<TokenUsage> usages) {
        StringBuilder sb = new StringBuilder();

        // 效果对比
        sb.append(generateMarkdown(results));

        // 成本分析
        if (usages != null && !usages.isEmpty()) {
            sb.append("\n## 成本分析\n\n");
            CostAnalysis costAnalysis = analyzeCost(usages);
            sb.append(costAnalysis.getSummary()).append("\n\n");
            sb.append(costAnalysis.getComparisonTable()).append("\n");
        }

        // 推荐方案
        sb.append("\n## 推荐方案\n\n");
        Recommendation recommendation = recommendBest(results);
        sb.append(recommendation.getRecommendationText()).append("\n");

        // 总结
        sb.append("\n## 总结\n\n");
        sb.append(generateSummary(results, usages));

        return sb.toString();
    }

    // ========== 辅助方法 ==========

    /**
     * 生成对比表格
     */
    private String generateComparisonTable(List<EvaluationResult> results) {
        StringBuilder sb = new StringBuilder();

        sb.append("| 方案 | 准确性 | 完整度 | 格式 | 综合评分 | 等级 | 主要问题 |\n");
        sb.append("|------|--------|--------|------|----------|------|----------|\n");

        for (int i = 0; i < results.size(); i++) {
            EvaluationResult r = results.get(i);
            String marker = (r.getOverallScore() == getMaxScore(results)) ? "⭐ " : "";
            String mainIssue = r.getIssues().isEmpty() ? "无" : r.getIssues().get(0);

            sb.append(String.format("| %s方案%d | %.2f | %.2f | %.2f | %.2f | %s | %s |\n",
                    marker, i + 1,
                    r.getAccuracyScore(),
                    r.getCompletenessScore(),
                    r.getFormatScore(),
                    r.getOverallScore(),
                    r.getGrade(),
                    mainIssue.length() > 15 ? mainIssue.substring(0, 15) + "..." : mainIssue
            ));
        }

        return sb.toString();
    }

    /**
     * 生成单个结果详情
     */
    private String generateResultDetail(EvaluationResult result) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("- **准确性**: %.2f\n", result.getAccuracyScore()));
        sb.append(String.format("- **完整度**: %.2f\n", result.getCompletenessScore()));
        sb.append(String.format("- **格式评分**: %.2f\n", result.getFormatScore()));
        sb.append(String.format("- **综合评分**: %.2f（%s）\n\n",
                result.getOverallScore(), result.getGrade()));

        if (!result.getStrengths().isEmpty()) {
            sb.append("**优点**:\n");
            for (String strength : result.getStrengths()) {
                sb.append(String.format("- %s\n", strength));
            }
            sb.append("\n");
        }

        if (!result.getIssues().isEmpty()) {
            sb.append("**问题**:\n");
            for (String issue : result.getIssues()) {
                sb.append(String.format("- %s\n", issue));
            }
            sb.append("\n");
        }

        sb.append(String.format("**建议**: %s\n\n", result.getRecommendation()));

        return sb.toString();
    }

    /**
     * 生成推荐理由
     */
    private String generateRecommendationReason(EvaluationResult best) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("综合评分 %.2f（%s），",
                best.getOverallScore(), best.getGrade()));

        if (!best.getStrengths().isEmpty()) {
            sb.append("优点：" + best.getStrengths().get(0));
        }

        if (best.getIssues().isEmpty()) {
            sb.append("，无明显问题");
        } else {
            sb.append("，但需注意" + best.getIssues().get(0));
        }

        return sb.toString();
    }

    /**
     * 生成总结
     */
    private String generateSummary(List<EvaluationResult> results, List<TokenUsage> usages) {
        StringBuilder sb = new StringBuilder();

        // 平均分
        double avgScore = results.stream()
                .mapToDouble(EvaluationResult::getOverallScore)
                .average()
                .orElse(0);

        sb.append(String.format("平均评分：%.2f\n", avgScore));

        // 合格率
        long passingCount = results.stream()
                .filter(EvaluationResult::isPassing)
                .count();
        sb.append(String.format("合格率：%d/%d（%.1f%%）\n",
                passingCount, results.size(), passingCount * 100.0 / results.size()));

        // 成本（如果有）
        if (usages != null && !usages.isEmpty()) {
            CostAnalysis cost = analyzeCost(usages);
            sb.append(String.format("总成本：$%.4f（约¥%.3f）\n",
                    cost.getTotalCostUSD(), cost.getTotalCostCNY()));
        }

        sb.append("\n建议：");
        Recommendation rec = recommendBest(results);
        sb.append(rec.getRecommendationText());

        return sb.toString();
    }

    /**
     * 获取最高评分
     */
    private double getMaxScore(List<EvaluationResult> results) {
        return results.stream()
                .mapToDouble(EvaluationResult::getOverallScore)
                .max()
                .orElse(0);
    }
}
