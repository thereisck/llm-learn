package com.ck.custom.llmlearn.prompt_optimizer.model;

import com.ck.custom.llmlearn.prompt_optimizer.client.TokenUsage;
import com.ck.custom.llmlearn.prompt_optimizer.engine.EvaluationResult;
import com.ck.custom.llmlearn.prompt_optimizer.report.CostAnalysis;
import com.ck.custom.llmlearn.prompt_optimizer.report.Recommendation;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * A/B测试结果DTO
 */
@Data
@Builder
public class ABTestResult {
    
    // 各方案详细结果
    private List<ABTestDetail> details;
    
    // 对比报告（Markdown）
    private String comparisonReport;
    
    // 推荐方案
    private Recommendation recommendation;
    
    // 成本分析
    private CostAnalysis costAnalysis;
    
    // 总耗时（毫秒）
    private long totalTimeMs;
    
    /**
     * A/B测试单个方案详情
     */
    @Data
    @Builder
    public static class ABTestDetail {
        
        private int index;
        private String prompt;
        private String response;
        private TokenUsage tokenUsage;
        private long latencyMs;
        private EvaluationResult evaluation;
    }
}