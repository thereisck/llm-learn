package com.ck.custom.llmlearn.domain;

import lombok.Data;

/**
 * 聊天请求类
 * @author changkong
 * @date 2026/4/12 22:36
 **/
@Data
public class ChatRequest {
    private String message;
    private String model;
}
