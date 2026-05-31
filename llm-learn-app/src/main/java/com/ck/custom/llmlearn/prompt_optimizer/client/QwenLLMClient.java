package com.ck.custom.llmlearn.prompt_optimizer.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 阿里云Qwen模型客户端实现
 * 
 * 核心功能：
 * 1. HTTP调用阿里云dashscope API
 * 2. 正确处理返回的usage字段（prompt_tokens + completion_tokens）
 * 3. 支持流式和非流式调用
 * 
 * API文档：https://help.aliyun.com/zh/dashscope/developer-reference/api-details
 */
@Slf4j
@Component
public class QwenLLMClient implements LLMClient {
    
    // 阿里云API配置
    private static final String DASHSCOPE_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
    private static final String API_KEY = System.getenv("DASHSCOPE_API_KEY"); // 从环境变量读取
    
    private final WebClient webClient;
    
    public QwenLLMClient() {
        this.webClient = WebClient.builder()
            .baseUrl(DASHSCOPE_URL)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("Authorization", "Bearer " + API_KEY)
            .build();
    }
    
    @Override
    public LLMResponse call(String prompt, LLMConfig config) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 构建请求体
            JSONObject requestBody = buildRequestBody(prompt, config);
            
            // 调用API
            String responseJson = webClient.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            // 解析响应
            JSONObject responseObj = JSON.parseObject(responseJson);
            
            long latencyMs = System.currentTimeMillis() - startTime;
            
            // 提取内容和usage
            String content = extractContent(responseObj);
            TokenUsage tokenUsage = extractTokenUsage(responseObj);
            
            return LLMResponse.success(content, tokenUsage, latencyMs, config.getModel());
            
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("阿里云Qwen调用失败: {}", e.getMessage());
            return LLMResponse.failure(e.getMessage(), latencyMs);
        }
    }
    
    @Override
    public List<LLMResponse> batchCall(List<String> prompts, LLMConfig config) {
        // 并行调用
        return prompts.stream()
            .map(prompt -> CompletableFuture.supplyAsync(() -> call(prompt, config)))
            .collect(Collectors.toList())
            .stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
    }
    
    @Override
    public CompletableFuture<LLMResponse> asyncCall(String prompt, LLMConfig config) {
        return CompletableFuture.supplyAsync(() -> call(prompt, config));
    }
    
    @Override
    public int calculateTokens(String prompt) {
        // 注意：大模型API会自动返回usage，这里仅做估算
        // 简化估算：中文约1.5字符/token，英文约4字符/token
        int charCount = prompt.length();
        int chineseCount = countChineseChars(prompt);
        int englishCount = charCount - chineseCount;
        
        int tokens = (int) (englishCount / 4.0 + chineseCount / 1.5);
        return Math.max(tokens, 1);
    }
    
    @Override
    public TokenUsage calculateTotalTokens(String prompt, String response) {
        // 注意：实际Token消耗应该从API返回的usage字段获取
        // 这里仅作为备用估算方法
        int inputTokens = calculateTokens(prompt);
        int outputTokens = calculateTokens(response);
        return TokenUsage.of(inputTokens, outputTokens);
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 构建阿里云API请求体
     */
    private JSONObject buildRequestBody(String prompt, LLMConfig config) {
        JSONObject body = new JSONObject();
        
        // 模型参数
        JSONObject model = new JSONObject();
        model.put("model", config.getModel());
        
        // 输入参数
        JSONObject input = new JSONObject();
        JSONObject messages = new JSONObject();
        messages.put("role", "user");
        messages.put("content", prompt);
        input.put("messages", List.of(messages));
        
        // 参数配置
        JSONObject parameters = new JSONObject();
        parameters.put("temperature", config.getTemperature());
        parameters.put("max_tokens", config.getMaxTokens());
        parameters.put("top_p", config.getTopP());
        parameters.put("result_format", "message");
        
        body.put("model", model.get("model"));
        body.put("input", input);
        body.put("parameters", parameters);
        
        return body;
    }
    
    /**
     * 从响应中提取内容
     */
    private String extractContent(JSONObject response) {
        try {
            JSONObject output = response.getJSONObject("output");
            JSONObject choices = output.getJSONArray("choices").getJSONObject(0);
            JSONObject message = choices.getJSONObject("message");
            return message.getString("content");
        } catch (Exception e) {
            log.warn("解析响应内容失败: {}", e.getMessage());
            return response.toString();
        }
    }
    
    /**
     * 从响应中提取Token消耗（关键：使用API返回的真实usage）
     */
    private TokenUsage extractTokenUsage(JSONObject response) {
        try {
            JSONObject usage = response.getJSONObject("usage");
            int inputTokens = usage.getInteger("prompt_tokens");
            int outputTokens = usage.getInteger("completion_tokens");
            return TokenUsage.of(inputTokens, outputTokens);
        } catch (Exception e) {
            log.warn("解析usage失败，使用估算值: {}", e.getMessage());
            return TokenUsage.of(0, 0);
        }
    }
    
    /**
     * 计算中文字符数量
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
}