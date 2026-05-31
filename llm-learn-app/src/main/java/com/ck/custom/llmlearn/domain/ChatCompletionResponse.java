package com.ck.custom.llmlearn.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author changkong
 * @date 2026/4/12 22:49
 **/
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionResponse implements Serializable {
    private String id; // 响应ID，唯一标识这次对话生成的响应。
    private String object; // 对象类型，通常用于指示这个响应的类型。
    private Long created; // 创建时间，表示这个响应何时被创建，通常是一个时间戳。
    private String model; // 模型名称，指示生成这个响应所使用的模型。
    private String systemFingerprint; // 系统指纹，用于追踪和验证响应的来源或版本。
    private List<ChatChoice> choices; // 选择列表，包含多个对话生成的选择，每个选择代表一个可能的回复。
    private Usage usage; // 使用情况，包含关于这次对话生成中模型使用情况的统计信息。
}
