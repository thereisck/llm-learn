package com.ck.custom.llmlearn.service.rag;

import com.ck.custom.llmlearn.domain.rag.RerankResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author changkong
 * @date 2026/5/17 19:44
 **/
@Service
public class RerankClient {

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${rag.rerank-model}")
    private String rerankModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();


    public List<RerankResult> rerank(String question, List<String> documents, int topN) {
        ensureApiKey();
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("文档列表不能为空");
        }
        try {

            // 步骤1: 构建请求URL → baseUrl + "/rerank"
            String url = baseUrl + "/rerank";
            // 步骤2: 构建请求头 → Authorization: Bearer apiKey, Content-Type: application/json
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("Content-Type", "application/json");
            // 步骤3: 构建请求体 → model, query, documents, return_documents=true, top_n=topN
            Map<String, Object> requestBody = Map.of(
                    "model", rerankModel,
                    "query", question,
                    "documents", documents,
                    "return_documents", true,
                    "top_n", topN
            );
            // 步骤4: 发送POST请求
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            // 步骤5: 解析响应 → 从results数组中提取每个元素的index + relevance_score + document.text
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode resultsArray = root.path("results");
            // 步骤6: 返回List<RerankResult>
            List<RerankResult> rerankResults = new ArrayList<>();
            for (JsonNode item : resultsArray) {
                int index = item.path("index").asInt();
                double relevanceScore = item.path("relevance_score").asDouble();
                String text = item.path("document").path("text").asText();
                rerankResults.add(new RerankResult(index, relevanceScore, text));
            }
            return rerankResults;
        } catch (Exception e) {
            throw new RuntimeException("调用Rerank接口失败", e);
        }
    }

    private void ensureApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("缺少 SILICONFLOW_API_KEY 环境变量");
        }
    }
}
