package com.ck.custom.llmlearn.prompt_optimizer.model;

import com.ck.custom.llmlearn.prompt_optimizer.client.*;
import com.ck.custom.llmlearn.prompt_optimizer.engine.*;
import lombok.Builder;
import lombok.Data;

/**
 * 测试结果DTO
 */
@Data
@Builder
public class TestResult {
    
    // Prompt内容
    private String prompt;
    
    // LLM响应
    private String response;
    
    // Token消耗
    private TokenUsage tokenUsage;
    
    // 响应延迟（毫秒）
    private long latencyMs;
    
    // 评估结果
    private EvaluationResult evaluation;
    
    // Markdown报告
    private String report;
    
    // 总耗时（毫秒）
    private long totalTimeMs;
    
    // 是否成功
    private boolean success;
}