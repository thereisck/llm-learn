package com.ck.custom.llmlearn.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * @author changkong
 * @date 2026/4/12 22:49
 **/
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatChoice implements Serializable {
    private Integer index; // 索引，表示这个选择在列表中的位置。
    /**
     * 当请求参数stream为true时，返回的是delta对象，
     * 表示消息的增量更新，用于流式传输部分消息内容。
     */
    private MessageResponse delta;
    /**
     * 当请求参数stream为false时，返回的是message对象，
     * 表示完整的消息响应，包含整个消息的内容。
     */
    private MessageResponse message;

    private String finish_reason; // 完成原因，表示为什么生成过程结束，例如正常完成、达到最大令牌数等。
}
