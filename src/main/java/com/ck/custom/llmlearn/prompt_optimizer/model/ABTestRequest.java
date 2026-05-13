package com.ck.custom.llmlearn.prompt_optimizer.model;

import com.ck.custom.llmlearn.prompt_optimizer.client.LLMConfig;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * A/B测试请求DTO
 */
@Data
public class ABTestRequest {
    
    // 多个Prompt方案（至少2个）
    private List<ABTestPromptItem> prompts;
    
    // 期望输出（用于评估）
    private String expectedOutput;
    
    // LLM配置（可选）
    private LLMConfig config;
    
    /**
     * A/B测试单个Prompt项
     */
    @Data
    public static class ABTestPromptItem {
        
        // 方式1：使用模板ID渲染
        private String templateId;
        private Map<String, String> params;
        
        // 方式2：直接提供Prompt
        private String prompt;
    }
}