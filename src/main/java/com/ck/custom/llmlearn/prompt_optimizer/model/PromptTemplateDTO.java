package com.ck.custom.llmlearn.prompt_optimizer.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * @author changkong
 * @date 2026/4/30 14:34
 **/
@Data
public class PromptTemplateDTO {

    private String id;
    private String name;
    private String category;
    private String template;
    private Map<String, String> variables;
    private String version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 元数据
    private Map<String, Object> metadata;

    // ========== 构造函数 ==========
    public PromptTemplateDTO() {
        this.variables = new HashMap<>();
        this.metadata = new HashMap<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.version = "1.0";
    }

    public PromptTemplateDTO(String id, String name, String template) {
        this();
        this.id = id;
        this.name = name;
        this.template = template;
    }

    // ========== 核心方法：渲染模板 ==========

    /**
     * 渲染模板：用参数替换占位符
     *
     * 占位符格式：${variableName}
     * 示例：template = "请翻译：${text}"，params = {"text": "Hello"}
     *       返回："请翻译：Hello"
     */
    public String render(Map<String, String> params) {
        if (template == null || template.isEmpty()) {
            throw new IllegalArgumentException("模板内容不能为空");
        }
        //替换所有变量
        String rendered = template;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            rendered = rendered.replace(placeholder, value);
        }

        //检查是否还有未替换的占位符
        if (rendered.matches(".*\\$\\{.+?}.*")) {
            throw new IllegalArgumentException("渲染失败，存在未替换的占位符: " + rendered);
        }

        this.updatedAt = LocalDateTime.now();
        return rendered;
    }
}
