package com.ck.custom.llmlearn.prompt_optimizer.client;

import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.stream.Collectors;

/**
 *  * Mock LLM客户端（用于测试）
 *  *
 *  * 不调用真实API，返回模拟响应
 *  * 适用场景：单元测试、快速验证逻辑
 * @author changkong
 * @date 2026/4/30 15:16
 **/
@Component
public class MockLLMClient implements LLMClient{

    private static final String MOCK_RESPONSE_TEMPLATE = "这是针对Prompt的模拟响应：%s";

    @Override
    public LLMResponse call(String prompt, LLMConfig config) {
        long startTime = System.currentTimeMillis();

        // 模拟延迟（100-300ms）
        simulateLatency(100, 300);

        long latencyMs = System.currentTimeMillis() - startTime;

        // 计算Token
        int inputTokens = calculateTokens(prompt);
        int outputTokens = estimateOutputTokens(prompt);
        TokenUsage tokenUsage = TokenUsage.of(inputTokens, outputTokens);

        // 生成模拟响应
        String content = generateMockResponse(prompt);

        return LLMResponse.success(content, tokenUsage, latencyMs, config.getModel());
    }

    @Override
    public List<LLMResponse> batchCall(List<String> prompts, LLMConfig config) {
        // 批量调用，返回每个Prompt的模拟响应
        return prompts.stream()
                .map(prompt -> CompletableFuture.supplyAsync(() -> call(prompt, config)))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    @Override
    public CompletableFuture<LLMResponse> asyncCall(String prompt, LLMConfig config) {
        // 异步调用，使用CompletableFuture模拟异步行为
        return CompletableFuture.supplyAsync(() -> call(prompt, config));
    }

    @Override
    public int calculateTokens(String prompt) {
        // 简单的Token计算：每4个字符算1个Token
        if (prompt == null || prompt.isEmpty()) {
            return 0;
        }

        // 简化估算：英文约4字符=1token，中文约1.5字符=1token
        int charCount = prompt.length();
        int chineseCount = countChineseChars(prompt);
        int englishCount = charCount - chineseCount;

        int tokens = (int) (englishCount / 4.0 + chineseCount / 1.5);
        return Math.max(tokens, 1);
    }

    @Override
    public TokenUsage calculateTotalTokens(String prompt, String response) {
        // 计算Token消耗详情
        int inputTokens = calculateTokens(prompt);
        int outputTokens = calculateTokens(response);
        return TokenUsage.of(inputTokens, outputTokens);
    }

        /**
        * 统计中文字符数量
        */
    private int countChineseChars(String text) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fff') {
                count++;
            }
        }
        return count;
    }

    /**
     * 生成模拟响应内容
     */
    private String generateMockResponse(String prompt) {
        // 根据Prompt长度生成不同响应
        if (prompt.length() < 50) {
            return "简短响应：" + prompt.substring(0, Math.min(20, prompt.length()));
        } else if (prompt.length() < 200) {
            return String.format(MOCK_RESPONSE_TEMPLATE, prompt.substring(0, 50) + "...");
        } else {
            return String.format(MOCK_RESPONSE_TEMPLATE,
                    "针对长Prompt的详细响应（" + prompt.length() + "字符）");
        }
    }

    /**
     * 估算输出Token数量（简单模拟：输入Token的50%-150%）
     */
    private int estimateOutputTokens(String prompt) {
        int inputTokens = calculateTokens(prompt);
        return Math.max(inputTokens / 2, 50);
    }

    /**
     * 模拟API延迟
     */
    private void simulateLatency(int minMs, int maxMs) {
        try {
            int latency = minMs + (int) (Math.random() * (maxMs - minMs));
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
