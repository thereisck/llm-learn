package com.ck.custom.llmlearn.prompt;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author changkong
 * @date 2026/4/29 23:19
 **/
@Data
public class PromptTemplateDO {
    /**
     * 模板唯一标识
     */
    private String id;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 模板版本
     */
    private String version;

    /**
     * 模板描述
     */
    private String description;

    /**
     * System Prompt（角色设定）
     */
    private String systemPrompt;

    /**
     * User Prompt 模板（用户输入模板）
     */
    private String userPromptTemplate;

    /**
     * 变量定义列表
     */
    private List<TemplateVariableDO> variables;

    /**
     * 元数据（作者、创建时间、标签等）
     */
    private Map<String, Object> metadata;

    /**
     * 获取变量的默认值Map
     */
    public Map<String, Object> getDefaultValues() {
        Map<String, Object> defaults = new HashMap<>();
        if(variables != null) {
            for(TemplateVariableDO var : variables) {
                if(var.getDefaultValue() != null) {
                    defaults.put(var.getName(), var.getDefaultValue());
                }
            }
        }
        return defaults;
    }

    /**
     * 获取必填变量列表
     */
    public List<String> getRequiredVariables() {
        List<String> requiredVars = new ArrayList<>();
        if (variables != null) {
            for (TemplateVariableDO var : variables) {
                if (var.isRequired()) {
                    requiredVars.add(var.getName());
                }
            }
        }
        return requiredVars;
    }
}
