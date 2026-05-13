package com.ck.custom.llmlearn.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.ck.custom.llmlearn.domain.ChatCompletionRequest;
import com.ck.custom.llmlearn.domain.ChatCompletionResponse;
import com.ck.custom.llmlearn.service.ChatCompletionService;
import com.ck.custom.llmlearn.utils.ModelMessageUtils;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * @author changkong
 * @date 2026/4/12 22:43
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatCompletionServiceImpl implements ChatCompletionService {

    @Resource
    private WebClient.Builder webClientBuilder;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${openai.api.base-url:https://api.openai.com/v1}")
    private String apiBaseUrl;

    @Override
    public Flux<ServerSentEvent<JSONObject>> completions(ChatCompletionRequest request) {
        // 使用 WebClient 发送 SSE 请求
        WebClient webClient = webClientBuilder
                .baseUrl(apiBaseUrl) // 设置目标 API 地址
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openaiApiKey) // 认证信息
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE) // 期望接收 SSE 格式的响应
                .build();
        StringBuilder contentBuilder = new StringBuilder(); // 用于累积 SSE 返回的消息内容
        return webClient.post()
                .uri("/chat/completions") // 补充具体的接口路径
                .bodyValue(request) // 发送请求体
                .retrieve()
                .bodyToFlux(String.class) // 解析响应数据为流式字符串
                .flatMap(data -> processResponse(data, contentBuilder,request)) // 逐个处理 SSE 数据
                .doOnCancel(() -> onClientCancel(contentBuilder))  // 监听前端主动断开连接
                .doOnTerminate(this::onStreamTerminate) // 监听 SSE 连接终止（可能是正常结束或异常）
                .onErrorResume(this::handleError); // 发生异常时进行错误处理
    }

    /**
     * 处理 SSE 响应数据
     *
     * @param data           SSE 返回的单条数据
     * @param contentBuilder 用于累积完整的返回内容
     * @return 处理后的 SSE 事件
     */
    private Mono<ServerSentEvent<JSONObject>> processResponse(String data, StringBuilder contentBuilder, ChatCompletionRequest request) {
        String normalized = normalizeSseData(data);
        // 判断是否为 SSE 结束标志 "[DONE]"
        if ("[DONE]".equals(normalized)) {
            String finalContent = contentBuilder.toString();
            log.info("SSE 消息接收完成，最终内容: {}", finalContent);
            // SSE 完成后，将内容保存到数据库
            saveToDatabase(finalContent);
            // 返回一个 SSE 事件，表示会话已完成
            return Mono.just(ServerSentEvent.<JSONObject>builder()
                    .event("done")
                    .id(UUID.randomUUID().toString())
                    .data(new JSONObject()) // 发送空数据，仅通知前端结束
                    .build());
        }

        try {
            // 解析 SSE 返回的 JSON 数据
            ChatCompletionResponse response = new ObjectMapper().readValue(normalized, ChatCompletionResponse.class);
            String content = "";
            if (Boolean.TRUE.equals(request.getStream())) {
                if (response.getChoices() != null
                        && !response.getChoices().isEmpty()
                        && response.getChoices().get(0).getDelta() != null) {
                    content = response.getChoices().get(0).getDelta().getContent();
                }
            } else {
                content = response.getChoices().get(0).getMessage().getContent();
            }
            // 累积返回的消息内容
            if ( content != null ) {
                contentBuilder.append(content);
            }

            // 构造 SSE 事件并返回
            return Mono.just(ServerSentEvent.<JSONObject>builder()
                    .event("add") // 事件类型
                    .id(UUID.randomUUID().toString()) // 生成唯一 ID
                    .data(new ObjectMapper().convertValue(response, JSONObject.class)) // 发送解析后的 JSON 数据
                    .build());
        } catch (Exception e) {
            log.error("JSON 解析失败: {}", normalized, e);
            return Mono.just(createErrorEvent("解析失败")); // 解析异常时返回错误事件
        }
    }

    private String normalizeSseData(String data) {
        if (data == null) {
            return "";
        }
        String trimmed = data.trim();
        if (trimmed.startsWith("data:")) {
            return trimmed.substring(5).trim();
        }
        return trimmed;
    }

    /**
     * 将完整的聊天内容保存到数据库
     *
     * @param content 完整的聊天记录
     */
    private void saveToDatabase(String content) {
        if ( content.isBlank() ) {
            return; // 空内容不保存
        }
        log.info("消息已成功入库:{}", content);
    }

    /**
     * 处理 SSE 请求中的异常情况
     *
     * @param e 异常对象
     * @return 错误事件的 Mono
     */
    private Mono<ServerSentEvent<JSONObject>> handleError(Throwable e) {
        log.error("SSE 请求处理异常", e);
        return Mono.just(createErrorEvent("服务异常，请联系管理员"));
    }

    /**
     * 创建 SSE 错误事件
     *
     * @param message 错误信息
     * @return SSE 错误事件
     */
    private ServerSentEvent<JSONObject> createErrorEvent(String message) {
        return ServerSentEvent.<JSONObject>builder()
                .event("error") // 事件类型为错误
                .id(UUID.randomUUID().toString()) // 生成唯一 ID
                .data(ModelMessageUtils.convertModelChatResponse(UUID.randomUUID().toString(), message)) // 发送错误信息
                .build();
    }

    /**
     * 监听前端主动关闭 SSE 连接
     */
    private void onClientCancel(StringBuilder contentBuilder) {
        System.out.println("11111----->" + contentBuilder);
        log.info("前端关闭了 SSE 连接");
    }

    /**
     * 监听 SSE 连接终止（包括正常结束或异常）
     */
    private void onStreamTerminate() {
        log.info("SSE 连接终止");
    }

}
