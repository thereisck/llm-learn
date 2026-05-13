package com.ck.custom.llmlearn.service.impl;

import com.ck.custom.llmlearn.domain.CodeAnnotateRequest;
import com.ck.custom.llmlearn.domain.CodeAnnotateResponse;
import com.ck.custom.llmlearn.service.CodeAnnotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CodeAnnotationServiceImpl implements CodeAnnotationService {

    private final WebClient.Builder webClientBuilder;

    @Value("${openai.api.base-url:https://api.openai.com/v1}")
    private String apiBaseUrl;

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.api.model:gpt-4o-mini}")
    private String model;

    @Override
    public CodeAnnotateResponse annotate(CodeAnnotateRequest request) {
        if (request == null || !StringUtils.hasText(request.getCode())) {
            throw new IllegalArgumentException("code must not be empty");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("openai.api.key is not configured");
        }

        String language = StringUtils.hasText(request.getLanguage()) ? request.getLanguage() : "Java";
        String prompt = buildPrompt(language, request.getCode());

        WebClient webClient = webClientBuilder.baseUrl(apiBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> reqBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt()),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.2
        );

        Map<String, Object> response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(reqBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        String annotated = extractContent(response);
        return new CodeAnnotateResponse(stripCodeFence(annotated));
    }

    private String systemPrompt() {
        return "You are a senior Java reviewer. Add complete, accurate, and concise comments to code without changing logic.";
    }

    private String buildPrompt(String language, String code) {
        return "Task: Add complete comments to the given " + language + " code.\n"
                + "Requirements:\n"
                + "1) Keep original logic and formatting as much as possible.\n"
                + "2) Add class-level, method-level, and key inline comments for important branches and variables.\n"
                + "3) Explain input/output and edge cases.\n"
                + "4) Return only final commented code, no markdown fences, no extra explanation.\n\n"
                + "Code:\n" + code;
    }

    private String extractContent(Map<String, Object> response) {
        if (response == null || response.get("choices") == null) {
            throw new IllegalStateException("invalid OpenAI response");
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices.isEmpty()) {
            throw new IllegalStateException("empty choices in OpenAI response");
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        Object content = message == null ? null : message.get("content");
        if (content == null) {
            throw new IllegalStateException("empty content in OpenAI response");
        }
        return String.valueOf(content);
    }

    private String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            String noHead = trimmed.replaceFirst("^```[a-zA-Z]*\\n", "");
            return noHead.substring(0, noHead.length() - 3).trim();
        }
        return trimmed;
    }
}
