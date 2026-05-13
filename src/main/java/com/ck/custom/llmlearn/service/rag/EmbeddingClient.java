package com.ck.custom.llmlearn.service.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * @author changkong
 * @date 2026/5/10 18:47
 **/
@Service
public class EmbeddingClient {
    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.embedding-model}")
    private String embeddingModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public double[] embed(String text) {
        ensureApiKey();
        try{
            String url = baseUrl + "/embeddings";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("Content-Type", "application/json");

            Map<String, Object> requestBody = Map.of(
                    "model", embeddingModel,
                    "input", text
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response  = restTemplate.postForEntity(url, request, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode array = root.path("data").get(0).path("embedding");
            double[] embedding = new double[array.size()];
            for (int i = 0; i < array.size(); i++) {
                embedding[i] = array.get(i).asDouble();
            }
            return embedding;
        }catch (Exception e) {
            throw new RuntimeException("调用LLM Embedding接口失败", e);
        }
    }

    private void ensureApiKey() {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("LLM API Key is not configured");
        }
    }
}
