package com.ck.custom.llmlearn.prompt_optimizer.model;

import com.ck.custom.llmlearn.prompt_optimizer.client.LLMConfig;
import lombok.Data;

import java.util.Map;

/**
 * 测试请求DTO
 */
@Data
public class TestRequest {
    
    // 方式1：使用模板ID渲染
    private String templateId;
    private Map<String, String> params;
    
    // 方式2：直接提供Prompt
    private String prompt;
    
    // 期望输出（用于评估）
    private String expectedOutput;
    
    // LLM配置（可选）
    private LLMConfig config;
}