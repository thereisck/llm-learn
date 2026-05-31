package com.ck.custom.llmlearn.prompt;

import lombok.Data;

import java.util.List;

/**
 * @author changkong
 * @date 2026/4/29 23:19
 **/
@Data
public class TemplateVariableDO {
    /**
     * 变量名
     */
    private String name;

    /**
     * 变量描述
     */
    private String description;

    /**
     * 变量类型: string, integer, enum, boolean
     */
    private String type;

    /**
     * 是否必填
     */
    private boolean required = false;

    /**
     * 默认值
     */
    private Object defaultValue;

    /**
     * enum类型的可选值列表
     */
    private List<String> options;

    //校验变量值是否合法
    public void validate(Object value) {
        //必填校验
        if(required && value == null) {
            throw new ValidationException("变量 '" + name + "' 是必填项，但未提供值");
        }

        //如果值为空且有默认值，使用默认值
        if(value == null && defaultValue != null) {;
            value = defaultValue;
        }

        //类型校验
        if(value != null) {
            validateType(value);
        }
    }

    /**
     * 类型校验
     */
    private void validateType(Object value) {
        String actualType = value.getClass().getSimpleName().toLowerCase();

        switch (type) {
            case "string":
                if (!(value instanceof String)) {
                    throw new ValidationException(
                            "变量 '" + name + "' 类型应为 string，实际为 " + actualType
                    );
                }
                break;
            case "integer":
                if (!(value instanceof Integer)) {
                    throw new ValidationException(
                            "变量 '" + name + "' 类型应为 integer，实际为 " + actualType
                    );
                }
                break;
            case "boolean":
                if (!(value instanceof Boolean)) {
                    throw new ValidationException(
                            "变量 '" + name + "' 类型应为 boolean，实际为 " + actualType
                    );
                }
                break;
            case "enum":
                if (options != null) {
                    if (!options.contains(value.toString())) {
                        throw new ValidationException(
                                "变量 '" + name + "' 的值 '" + value + "' 不在可选范围内: " + options
                        );
                    }
                }
                break;
            default:
                // 其他类型不做严格校验
        }
    }
}
