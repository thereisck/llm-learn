package com.ck.custom.llmlearn.prompt_optimizer.client;

import lombok.Data;

import java.time.LocalDateTime;

/**
 *  * 核心字段：
 *  * - content: LLM生成的内容
 *  * - tokenUsage: Token消耗统计
 *  * - latency: 响应延迟（毫秒）
 *  * - model: 使用的模型名称
 * @author changkong
 * @date 2026/4/30 15:09
 **/
@Data
public class LLMResponse {

    private String content;
    private TokenUsage tokenUsage;
    private long latencyMs;
    private String model;
    private LocalDateTime createdAt;
    private boolean success;
    private String errorMessage;

    // ========== 构造函数 ==========
    public LLMResponse() {
        this.createdAt = LocalDateTime.now();
        this.success = true;
    }

    public LLMResponse(String content, TokenUsage tokenUsage, long latencyMs, String model) {
        this();
        this.content = content;
        this.tokenUsage = tokenUsage;
        this.latencyMs = latencyMs;
        this.model = model;
    }

    // ========== 静态工厂方法 ==========
    /**
     * 成功响应
     */
    public static LLMResponse success(String content, TokenUsage tokenUsage, long latencyMs, String model) {
        return new LLMResponse(content, tokenUsage, latencyMs, model);
    }

    /**
     * 失败响应
     */
    public static LLMResponse failure(String errorMessage, long latencyMs) {
        LLMResponse response = new LLMResponse();
        response.success = false;
        response.errorMessage = errorMessage;
        response.latencyMs = latencyMs;
        return response;
    }

    /**
     * 获取成本估算（按OpenAI价格计算）
     *
     * GPT-3.5-turbo: $0.0015 / 1K input tokens, $0.002 / 1K output tokens
     * GPT-4: $0.03 / 1K input tokens, $0.06 / 1K output tokens
     */
    public double calculateCostUSD() {
        if (tokenUsage == null) {
            return 0.0;
        }

        // 简化计算：假设使用GPT-3.5-turbo
        double inputCost = tokenUsage.getInputTokens() * 0.0015 / 1000;
        double outputCost = tokenUsage.getOutputTokens() * 0.002 / 1000;

        return inputCost + outputCost;
    }
}
