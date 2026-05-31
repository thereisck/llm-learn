package com.ck.custom.llmlearn.prompt_optimizer.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 *  * 简化版评估引擎实现
 *  *
 *  * 评估策略：
 *  * - 准确性：与期望输出的相似度（关键词匹配）
 *  * - 完整度：期望要点覆盖率
 *  * - 格式一致性：格式特征检测
 *  *
 *  * 适用场景：快速测试、初步评估
 * @author changkong
 * @date 2026/4/30 16:03
 **/
@Component
public class SimpleEvaluationEngine implements EvaluationEngine {
    @Override
    public EvaluationResult evaluate(String response, String expectedOutput) {
        if (response == null || response.isEmpty()) {
            return createEmptyResult();
        }

        EvaluationResult result = new EvaluationResult();
        result.setResponseId("response-" + System.currentTimeMillis());

        // 评分计算
        double accuracy = calculateAccuracy(response, expectedOutput);
        double completeness = calculateCompleteness(response, expectedOutput);
        double format = calculateFormatScore(response);

        result.setAccuracyScore(accuracy);
        result.setCompletenessScore(completeness);
        result.setFormatScore(format);

        // 分析问题和优点
        analyzeIssues(response, result);
        analyzeStrengths(response, result);

        // 生成建议
        generateRecommendation(result);

        return result;
    }

    @Override
    public List<EvaluationResult> batchEvaluate(List<String> responses, String expectedOutput) {
        return responses.stream()
                .map(response -> evaluate(response, expectedOutput))
                .collect(Collectors.toList());
    }

    @Override
    public QualityScore analyzeQuality(String response) {
        QualityScore score = new QualityScore();

        if (response == null || response.isEmpty()) {
            return score;
        }

        // 准确性：关键词密度
        score.setDimension(QualityScore.ACCURACY, calculateKeywordDensity(response));

        // 流畅性：句子结构
        score.setDimension(QualityScore.FLUENCY, calculateFluency(response));

        // 一致性：逻辑连贯
        score.setDimension(QualityScore.CONSISTENCY, calculateConsistency(response));

        // 简洁性：冗余检测
        score.setDimension(QualityScore.CONCISENESS, calculateConciseness(response));

        // 专业性：术语检测
        score.setDimension(QualityScore.PROFESSIONALISM, calculateProfessionalism(response));

        return score;
    }

    @Override
    public ComparisonAnalysis compare(String response1, String response2) {
        ComparisonAnalysis analysis = new ComparisonAnalysis("resp1", "resp2");

        // 找共同点
        List<String> commonKeywords = findCommonKeywords(response1, response2);
        commonKeywords.forEach(analysis::addCommonPoint);

        // 找差异
        List<String> diffKeywords = findDifferentKeywords(response1, response2);
        diffKeywords.forEach(analysis::addDifference);

        // 评分对比
        EvaluationResult result1 = evaluate(response1, null);
        EvaluationResult result2 = evaluate(response2, null);

        analysis.setAdvantage1(String.format("评分 %.2f", result1.getOverallScore()));
        analysis.setAdvantage2(String.format("评分 %.2f", result2.getOverallScore()));

        // 推荐
        if (result1.getOverallScore() >= result2.getOverallScore()) {
            analysis.recommend(1, "综合评分更高");
        } else {
            analysis.recommend(2, "综合评分更高");
        }

        return analysis;
    }

    // ========== 评分计算方法 ==========

    /**
     * 计算准确性：与期望输出的关键词匹配度
     */
    private double calculateAccuracy(String response, String expectedOutput) {
        if (expectedOutput == null || expectedOutput.isEmpty()) {
            // 无期望输出时，基于内容长度给基础分
            return Math.min(response.length() / 10.0, 100.0);
        }

        // 提取期望关键词
        List<String> expectedKeywords = extractKeywords(expectedOutput);
        List<String> responseKeywords = extractKeywords(response);

        // 计算匹配率
        int matchedCount = 0;
        for (String keyword : expectedKeywords) {
            if (responseKeywords.contains(keyword)) {
                matchedCount++;
            }
        }

        return expectedKeywords.isEmpty() ? 0 :
                (matchedCount * 100.0 / expectedKeywords.size());
    }

    /**
     * 计算完整度：要点覆盖率
     */
    private double calculateCompleteness(String response, String expectedOutput) {
        if (expectedOutput == null || expectedOutput.isEmpty()) {
            return 100.0; // 无期望时默认完整
        }

        // 检查期望输出中的要点是否被覆盖
        int totalPoints = countKeyPoints(expectedOutput);
        int coveredPoints = countCoveredPoints(response, expectedOutput);

        return totalPoints == 0 ? 100.0 :
                (coveredPoints * 100.0 / totalPoints);
    }

    /**
     * 计算格式评分：格式特征检测
     */
    private double calculateFormatScore(String response) {
        double score = 100.0;

        // 检查格式问题
        if (!response.contains("\n")) {
            score -= 20; // 无换行扣分
        }

        if (response.contains("```") && !response.contains("``` ")) {
            score -= 10; // 代码块格式不规范
        }

        if (response.length() > 500 && !response.contains("##")) {
            score -= 15; // 长内容无结构扣分
        }

        return Math.max(score, 0);
    }

    // ========== 质量维度计算 ==========

    private double calculateKeywordDensity(String text) {
        List<String> keywords = extractKeywords(text);
        return keywords.size() > 0 ? Math.min(keywords.size() * 10, 100) : 50;
    }

    private double calculateFluency(String text) {
        // 检查句子结构
        int sentenceCount = text.split("[.!?]").length;
        int avgSentenceLength = text.length() / Math.max(sentenceCount, 1);

        // 平均句子长度在15-30之间为流畅
        if (avgSentenceLength >= 15 && avgSentenceLength <= 30) {
            return 90;
        } else if (avgSentenceLength < 15) {
            return 70; // 太短
        } else {
            return 60; // 太长
        }
    }

    private double calculateConsistency(String text) {
        // 检查逻辑连贯性（简化：检查是否有转折词）
        if (text.contains("因此") || text.contains("所以") || text.contains("但是")) {
            return 85;
        }
        return 70;
    }

    private double calculateConciseness(String text) {
        // 检查冗余（重复词汇）
        List<String> words = Arrays.asList(text.split("\\s+"));
        int uniqueCount = new HashSet<>(words).size();
        int totalCount = words.size();

        double ratio = uniqueCount * 100.0 / totalCount;
        return ratio > 70 ? ratio : 50;
    }

    private double calculateProfessionalism(String text) {
        // 检查专业术语（简化版）
        String[] professionalTerms = {"API", "框架", "架构", "优化", "性能", "系统"};
        int count = 0;
        for (String term : professionalTerms) {
            if (text.contains(term)) count++;
        }
        return Math.min(count * 20, 100);
    }

    // ========== 辅助方法 ==========

    private List<String> extractKeywords(String text) {
        // 简化：提取2-5字符的词汇
        String[] words = text.split("[\\s,，。！？.!?\n]+");
        return Arrays.stream(words)
                .filter(w -> w.length() >= 2 && w.length() <= 5)
                .distinct()
                .collect(Collectors.toList());
    }

    private int countKeyPoints(String text) {
        // 简化：按句号分割
        return text.split("[.。]").length;
    }

    private int countCoveredPoints(String response, String expected) {
        // 检查期望的每个句子是否在响应中出现
        String[] points = expected.split("[.。]");
        int count = 0;
        for (String point : points) {
            if (response.contains(point.trim())) {
                count++;
            }
        }
        return count;
    }

    private List<String> findCommonKeywords(String text1, String text2) {
        List<String> keywords1 = extractKeywords(text1);
        List<String> keywords2 = extractKeywords(text2);

        return keywords1.stream()
                .filter(keywords2::contains)
                .limit(5)
                .collect(Collectors.toList());
    }

    private List<String> findDifferentKeywords(String text1, String text2) {
        List<String> keywords1 = extractKeywords(text1);
        List<String> keywords2 = extractKeywords(text2);

        List<String> diffs = new ArrayList<>();
        keywords1.stream().filter(k -> !keywords2.contains(k)).limit(3).forEach(diffs::add);
        keywords2.stream().filter(k -> !keywords1.contains(k)).limit(3).forEach(diffs::add);

        return diffs;
    }

    private void analyzeIssues(String response, EvaluationResult result) {
        if (response.length() < 50) {
            result.addIssue("响应过短（<50字符）");
        }
        if (response.contains("我觉得") || response.contains("我认为")) {
            result.addIssue("包含主观表述");
        }
        if (!response.contains("。") && response.length() > 100) {
            result.addIssue("长文本无标点");
        }
    }

    private void analyzeStrengths(String response, EvaluationResult result) {
        if (response.contains("```")) {
            result.addStrength("包含代码示例");
        }
        if (response.contains("##") || response.contains("-")) {
            result.addStrength("结构清晰");
        }
        if (response.length() > 200 && response.length() < 1000) {
            result.addStrength("篇幅适中");
        }
    }

    private void generateRecommendation(EvaluationResult result) {
        if (result.getOverallScore() >= 80) {
            result.setRecommendation("质量优秀，可直接使用");
        } else if (result.getOverallScore() >= 60) {
            result.setRecommendation("质量合格，建议优化细节");
        } else {
            result.setRecommendation("质量不足，需要重新生成");
        }
    }

    private EvaluationResult createEmptyResult() {
        EvaluationResult result = new EvaluationResult();
        result.setResponseId("empty");
        result.addIssue("响应为空");
        result.setRecommendation("需要重新生成");
        return result;
    }

}
