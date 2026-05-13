package com.ck.custom.llmlearn.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * @author changkong
 * @date 2026/4/12 22:41
 **/
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Tool implements Serializable {
    private String type; // 工具类型，表示工具的种类或分类。
    private Object function; // 函数对象，可以是具体的函数对象或者为null，表示工具所关联的函数。
    private String knowledge_id; // 知识ID，用于标识与工具相关的知识或数据。
    private String prompt_template; // 提示模板，用于生成提示信息的模板字符串。
    private Boolean enable; // 是否启用，表示工具是否被启用。
    private String search_query; // 搜索查询，用于存储搜索查询的字符串。
    private Boolean search_result; // 搜索结果，表示是否获取了搜索结果。

}
