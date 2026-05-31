package com.ck.custom.llmlearn.prompt_optimizer.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * SiliconFlow LLM客户端（OpenAI兼容格式）
 * 
 * 支持OpenAI兼容API：
 * - SiliconFlow: https://api.siliconflow.cn/v1
 * - GLM-5.1、Qwen等模型
 * 
 * API文档：https://siliconflow.cn/docs
 */
@Slf4j
@Component
public class SiliconFlowLLMClient implements LLMClient {
    
    @Value("${openai.api.base-url:https://api.siliconflow.cn/v1}")
    private String baseUrl;
    
    @Value("${openai.api.key}")
    private String apiKey;
    
    @Value("${openai.api.model:Pro/zai-org/GLM-5.1}")
    private String defaultModel;
    
    private WebClient webClient;
    
    public SiliconFlowLLMClient() {
        // WebClient 在初始化时无法注入 @Value，需要在方法中创建
    }
    
    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        }
        return webClient;
    }
    
    @Override
    public LLMResponse call(String prompt, LLMConfig config) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 构建OpenAI格式请求体
            JSONObject requestBody = buildOpenAIRequest(prompt, config);
            
            log.info("调用SiliconFlow API: model={}, prompt长度={}", 
                requestBody.getString("model"), 
                prompt.length());
            log.debug("请求体: {}", requestBody.toString());
            
            // 调用API
            String responseJson = getWebClient().post()
                .uri("/chat/completions")
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
            
            log.info("SiliconFlow调用成功: latency={}ms, inputTokens={}, outputTokens={}", 
                latencyMs, tokenUsage.getInputTokens(), tokenUsage.getOutputTokens());
            
            return LLMResponse.success(content, tokenUsage, latencyMs, 
                requestBody.getString("model"));
            
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("SiliconFlow调用失败: {}", e.getMessage(), e);
            
            // 返回失败响应，确保 tokenUsage 不为 null
            LLMResponse response = LLMResponse.failure(e.getMessage(), latencyMs);
            response.setTokenUsage(TokenUsage.of(0, 0));
            return response;
        }
    }
    
    @Override
    public List<LLMResponse> batchCall(List<String> prompts, LLMConfig config) {
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
        if (prompt == null || prompt.isEmpty()) {
            return 0;
        }
        
        int charCount = prompt.length();
        int chineseCount = countChineseChars(prompt);
        int englishCount = charCount - chineseCount;
        
        int tokens = (int) (englishCount / 4.0 + chineseCount / 1.5);
        return Math.max(tokens, 1);
    }
    
    @Override
    public TokenUsage calculateTotalTokens(String prompt, String response) {
        int inputTokens = calculateTokens(prompt);
        int outputTokens = calculateTokens(response);
        return TokenUsage.of(inputTokens, outputTokens);
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 构建OpenAI格式请求体
     */
    private JSONObject buildOpenAIRequest(String prompt, LLMConfig config) {
        JSONObject body = new JSONObject();
        
        // 模型：如果config.getModel()是默认值(gpt-3.5-turbo)，替换为SiliconFlow模型
        String model = config.getModel();
        if (model == null || "gpt-3.5-turbo".equals(model) || "gpt-4".equals(model)) {
            model = defaultModel;
        }
        body.put("model", model);
        
        // 消息（OpenAI格式）
        JSONArray messages = new JSONArray();
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);
        body.put("messages", messages);
        
        // 参数（注意：LLMConfig使用基本类型，不能用 != null）
        body.put("temperature", config.getTemperature());
        body.put("max_tokens", config.getMaxTokens());
        body.put("top_p", config.getTopP());
        
        return body;
    }
    
    /**
     * 从OpenAI格式响应中提取内容
     */
    private String extractContent(JSONObject response) {
        try {
            JSONArray choices = response.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject choice = choices.getJSONObject(0);
                JSONObject message = choice.getJSONObject("message");
                return message.getString("content");
            }
        } catch (Exception e) {
            log.warn("解析响应内容失败: {}", e.getMessage());
        }
        return "解析失败: " + response.toString();
    }
    
    /**
     * 从OpenAI格式响应中提取Token消耗
     */
    private TokenUsage extractTokenUsage(JSONObject response) {
        try {
            JSONObject usage = response.getJSONObject("usage");
            if (usage != null) {
                int inputTokens = usage.getInteger("prompt_tokens");
                int outputTokens = usage.getInteger("completion_tokens");
                return TokenUsage.of(inputTokens, outputTokens);
            }
        } catch (Exception e) {
            log.warn("解析usage失败: {}", e.getMessage());
        }
        return TokenUsage.of(0, 0);
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