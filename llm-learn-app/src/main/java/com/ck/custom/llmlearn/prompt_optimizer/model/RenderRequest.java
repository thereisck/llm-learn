package com.ck.custom.llmlearn.prompt_optimizer.model;

import lombok.Data;

import java.util.Map;

/**
 * 模板渲染请求DTO
 */
@Data
public class RenderRequest {
    
    private String templateId;
    private Map<String, String> params;
}