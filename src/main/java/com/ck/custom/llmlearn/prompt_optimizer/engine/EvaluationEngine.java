package com.ck.custom.llmlearn.prompt_optimizer.engine;

import java.util.List;

/**
 *  * 核心功能：
 *  * - 准确性评分（答案是否正确）
 *  * - 完整度评分（是否覆盖所有要点）
 *  * - 格式一致性（是否符合期望格式）
 *  * - 质量综合评分（加权平均）
 * @author changkong
 * @date 2026/4/30 15:50
 **/
public interface EvaluationEngine {
    /**
     * 单次评估：评估单个LLM响应的质量
     *
     * @param response LLM响应内容
     * @param expectedOutput 期望输出（用于对比）
     * @return 评估结果
     */
    EvaluationResult evaluate(String response, String expectedOutput);

    /**
     * 批量评估：评估多个LLM响应
     *
     * @param responses LLM响应列表
     * @param expectedOutput 期望输出
     * @return 评估结果列表
     */
    List<EvaluationResult> batchEvaluate(List<String> responses, String expectedOutput);

    /**
     * 质量评分：分析响应的质量维度
     *
     * @param response LLM响应内容
     * @return 质量评分对象
     */
    QualityScore analyzeQuality(String response);

    /**
     * 对比评估：对比两个响应的差异
     *
     * @param response1 第一个响应
     * @param response2 第二个响应
     * @return 对比分析结果
     */
    ComparisonAnalysis compare(String response1, String response2);
}
