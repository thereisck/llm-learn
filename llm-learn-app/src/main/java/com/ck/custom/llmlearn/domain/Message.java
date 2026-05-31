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
public class Message implements Serializable {
    private String role; // 消息角色，表示消息的发送者身份，例如用户、助手等。
    private String content; // 消息内容，包含发送者所传递的文本信息。
}
