package com.ck.custom.llmlearn.prompt_optimizer.report;

import com.ck.custom.llmlearn.prompt_optimizer.client.TokenUsage;
import com.ck.custom.llmlearn.prompt_optimizer.engine.EvaluationResult;

import java.util.List;

/**
 *  * 对比报告生成器接口
 *  *
 *  * 核心功能：
 *  * - 生成Markdown对比表格
 *  * - 成本分析（Token消耗对比）
 *  * - 推荐最优方案
 *  * - 导出完整报告
 * @author changkong
 * @date 2026/4/30 16:12
 **/
public interface ComparisonReport {
    /**
     * 生成Markdown格式对比报告
     *
     * @param results 评估结果列表
     * @return Markdown文本
     */
    String generateMarkdown(List<EvaluationResult> results);

    /**
     * 成本分析：对比Token消耗和费用
     *
     * @param usages Token消耗列表
     * @return 成本分析报告
     */
    CostAnalysis analyzeCost(List<TokenUsage> usages);

    /**
     * 推荐最优方案
     *
     * @param results 评估结果列表
     * @return 推荐结果（包含推荐理由）
     */
    Recommendation recommendBest(List<EvaluationResult> results);

    /**
     * 导出完整报告（Markdown + 成本 + 推荐）
     *
     * @param results 评估结果列表
     * @param usages Token消耗列表
     * @return 完整报告文本
     */
    String exportFullReport(List<EvaluationResult> results, List<TokenUsage> usages);
}
