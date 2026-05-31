package com.ck.custom.llmlearn.prompt_optimizer.client;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /**
 *  * LLM API客户端接口
 *  *
 *  * 核心功能：
 *  * - 批量并行调用（多Prompt对比）
 *  * - 异步调用（CompletableFuture）
 *  * - Token统计（成本计算）
 * @author changkong
 * @date 2026/4/30 15:05
 **/
public interface LLMClient {

    /**
     * 单次调用LLM
     *
     * @param prompt Prompt字符串
     * @param config 配置参数（model、temperature、max_tokens等）
     * @return LLM响应结果
     */
    LLMResponse call(String prompt, LLMConfig config);

    /**
     * 批量并行调用（用于对比不同Prompt效果）
     *
     * @param prompts 多个Prompt字符串列表
     * @param config 配置参数
     * @return 对应的响应列表
     */
    List<LLMResponse> batchCall(List<String> prompts, LLMConfig config);

    /**
     * 异步调用（用于流式处理）
     *
     * @param prompt Prompt字符串
     * @param config 配置参数
     * @return CompletableFuture包装的响应
     */
    CompletableFuture<LLMResponse> asyncCall(String prompt, LLMConfig config);

    /**
     * 计算Prompt的Token数量
     *
     * @param prompt Prompt字符串
     * @return Token数量
     */
    int calculateTokens(String prompt);

    /**
     * 计算Prompt和Response的总Token消耗
     *
     * @param prompt 输入Prompt
     * @param response 输出响应
     * @return Token消耗详情
     */
    TokenUsage calculateTotalTokens(String prompt, String response);
}
