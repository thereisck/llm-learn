package com.ck.custom.llmlearn.contoller;

import com.alibaba.fastjson2.JSONObject;
import com.ck.custom.llmlearn.domain.ChatCompletionRequest;
import com.ck.custom.llmlearn.service.ChatCompletionService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * @author changkong
 * @date 2026/4/12 22:36
 **/
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/chat")
public class ChatController {

    @Resource
    private ChatCompletionService chatCompletionService;

    @PostMapping(value = "/completions")
    public Flux<ServerSentEvent<JSONObject>> completions(@RequestBody ChatCompletionRequest chatCompletionRequest) {
        return chatCompletionService.completions(chatCompletionRequest);
    }
}
