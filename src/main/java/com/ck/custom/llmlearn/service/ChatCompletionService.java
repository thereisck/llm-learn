package com.ck.custom.llmlearn.service;

import com.alibaba.fastjson2.JSONObject;
import com.ck.custom.llmlearn.domain.ChatCompletionRequest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * @author changkong
 * @date 2026/4/12 22:37
 **/
public interface ChatCompletionService {
    Flux<ServerSentEvent<JSONObject>> completions(ChatCompletionRequest request);
}
