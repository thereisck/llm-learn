package com.ck.custom.llmlearn.prompt_optimizer.engine;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ck.custom.llmlearn.prompt_optimizer.client.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * LLM-as-a-Judge评估引擎
 * 
 * 核心思想：用大模型评估大模型的输出质量
 * 
 * 评估流程：
 * 1. 构建评估Prompt（让LLM扮演"评估专家"角色）
 * 2. 调用Qwen模型进行评估
 * 3. 解析评估结果（评分、问题、建议）
 * 
 * 相比关键词匹配的优势：
 * - 更准确的语义理解
 * - 能识别逻辑漏洞
 * - 能给出具体改进建议
 */
@Slf4j
@Component
public class LLMEvaluationEngine implements EvaluationEngine {
    
    @Autowired
    private SiliconFlowLLMClient llmClient; // SiliconFlow客户端（OpenAI兼容）
    
    // 评估Prompt模板
    private static final String EVALUATION_PROMPT_TEMPLATE = """
        你是一名专业的文本质量评估专家。请对以下内容进行客观评估。
        
        # 待评估内容
        {response}
        
        # 期望输出（参考）
        {expected}
        
        # 评估要求
        请从以下维度进行评估，每个维度给出0-100分的评分：
        
        1. **准确性**：事实是否正确，是否与期望输出一致
        2. **流畅性**：语言是否自然，句子结构是否合理
        3. **专业度**：术语使用是否准确，是否有专业深度
        4. **完整度**：是否覆盖所有要点，是否有遗漏
        5. **格式规范**：格式是否清晰，结构是否合理
        
        # 输出格式（JSON）
        请按以下JSON格式输出评估结果：
        
        ```json
        {
          "accuracy_score": [评分],
          "fluency_score": [评分],
          "professionalism_score": [评分],
          "completeness_score": [评分],
          "format_score": [评分],
          "overall_score": [加权平均评分],
          "issues": ["问题1", "问题2"],
          "strengths": ["优点1", "优点2"],
          "recommendation": "改进建议"
        }
        ```
        
        请确保输出为纯JSON格式，不要添加额外说明。
        """;
    
    @Override
    public EvaluationResult evaluate(String response, String expectedOutput) {
        if (response == null || response.isEmpty()) {
            return createEmptyResult();
        }
        
        try {
            // 构建评估Prompt
            String evaluationPrompt = buildEvaluationPrompt(response, expectedOutput);
            
            // 调用LLM进行评估
            LLMConfig config = LLMConfig.defaultConfig();
            LLMResponse llmResponse = llmClient.call(evaluationPrompt, config);
            
            if (!llmResponse.isSuccess()) {
                log.warn("LLM评估失败，降级为简单评估");
                return fallbackEvaluate(response, expectedOutput);
            }
            
            // 解析评估结果
            return parseEvaluationResult(llmResponse.getContent());
            
        } catch (Exception e) {
            log.error("LLM评估异常: {}", e.getMessage());
            return fallbackEvaluate(response, expectedOutput);
        }
    }
    
    @Override
    public List<EvaluationResult> batchEvaluate(List<String> responses, String expectedOutput) {
        return responses.stream()
            .map(response -> evaluate(response, expectedOutput))
            .collect(Collectors.toList());
    }
    
    @Override
    public QualityScore analyzeQuality(String response) {
        // 构建质量分析Prompt
        String qualityPrompt = buildQualityPrompt(response);
        
        try {
            LLMConfig config = LLMConfig.defaultConfig();
            LLMResponse llmResponse = llmClient.call(qualityPrompt, config);
            
            if (llmResponse.isSuccess()) {
                return parseQualityScore(llmResponse.getContent());
            }
            
        } catch (Exception e) {
            log.error("质量分析失败: {}", e.getMessage());
        }
        
        // 降级为简单评估
        return fallbackQualityScore(response);
    }
    
    @Override
    public ComparisonAnalysis compare(String response1, String response2) {
        // 构建对比Prompt
        String comparisonPrompt = buildComparisonPrompt(response1, response2);
        
        try {
            LLMConfig config = LLMConfig.defaultConfig();
            LLMResponse llmResponse = llmClient.call(comparisonPrompt, config);
            
            if (llmResponse.isSuccess()) {
                return parseComparisonAnalysis(llmResponse.getContent());
            }
            
        } catch (Exception e) {
            log.error("对比分析失败: {}", e.getMessage());
        }
        
        // 降级为简单对比
        return fallbackCompare(response1, response2);
    }
    
    // ========== Prompt构建方法 ==========
    
    private String buildEvaluationPrompt(String response, String expected) {
        return EVALUATION_PROMPT_TEMPLATE
            .replace("{response}", response)
            .replace("{expected}", expected != null ? expected : "无特定期望输出");
    }
    
    private String buildQualityPrompt(String response) {
        return """
            请分析以下内容的质量，从准确性、流畅性、一致性、简洁性、专业性五个维度评分（0-100分）：
            
            # 内容
            %s
            
            # 输出格式（JSON）
            ```json
            {
              "accuracy": [评分],
              "fluency": [评分],
              "consistency": [评分],
              "conciseness": [评分],
              "professionalism": [评分]
            }
            ```
            """.formatted(response);
    }
    
    private String buildComparisonPrompt(String response1, String response2) {
        return """
            请对比以下两个方案的质量，给出推荐意见：
            
            # 方案1
            %s
            
            # 方案2
            %s
            
            # 输出格式（JSON）
            ```json
            {
              "common_points": ["共同点1", "共同点2"],
              "differences": ["差异1", "差异2"],
              "advantage_1": "方案1的优势",
              "advantage_2": "方案2的优势",
              "preferred": 1或2,
              "recommendation": "推荐理由"
            }
            ```
            """.formatted(response1, response2);
    }
    
    // ========== 结果解析方法 ==========
    
    private EvaluationResult parseEvaluationResult(String jsonContent) {
        try {
            // 提取JSON部分
            String json = extractJson(jsonContent);
            JSONObject obj = JSON.parseObject(json);
            
            EvaluationResult result = new EvaluationResult();
            result.setResponseId("llm-eval-" + System.currentTimeMillis());
            result.setAccuracyScore(obj.getDouble("accuracy_score"));
            result.setCompletenessScore(obj.getDouble("completeness_score"));
            result.setFormatScore(obj.getDouble("format_score"));
            
            // 解析问题和优点
            List<String> issues = obj.getJSONArray("issues")
                .stream().map(Object::toString).collect(Collectors.toList());
            result.getIssues().addAll(issues);
            
            List<String> strengths = obj.getJSONArray("strengths")
                .stream().map(Object::toString).collect(Collectors.toList());
            result.getStrengths().addAll(strengths);
            
            result.setRecommendation(obj.getString("recommendation"));
            
            return result;
            
        } catch (Exception e) {
            log.warn("解析评估结果失败: {}", e.getMessage());
            return createEmptyResult();
        }
    }
    
    private QualityScore parseQualityScore(String jsonContent) {
        try {
            String json = extractJson(jsonContent);
            JSONObject obj = JSON.parseObject(json);
            
            QualityScore score = new QualityScore();
            score.setDimension(QualityScore.ACCURACY, obj.getDouble("accuracy"));
            score.setDimension(QualityScore.FLUENCY, obj.getDouble("fluency"));
            score.setDimension(QualityScore.CONSISTENCY, obj.getDouble("consistency"));
            score.setDimension(QualityScore.CONCISENESS, obj.getDouble("conciseness"));
            score.setDimension(QualityScore.PROFESSIONALISM, obj.getDouble("professionalism"));
            
            return score;
            
        } catch (Exception e) {
            return new QualityScore();
        }
    }
    
    private ComparisonAnalysis parseComparisonAnalysis(String jsonContent) {
        try {
            String json = extractJson(jsonContent);
            JSONObject obj = JSON.parseObject(json);
            
            ComparisonAnalysis analysis = new ComparisonAnalysis("resp1", "resp2");
            
            List<String> commonPoints = obj.getJSONArray("common_points")
                .stream().map(Object::toString).collect(Collectors.toList());
            commonPoints.forEach(analysis::addCommonPoint);
            
            List<String> differences = obj.getJSONArray("differences")
                .stream().map(Object::toString).collect(Collectors.toList());
            differences.forEach(analysis::addDifference);
            
            analysis.setAdvantage1(obj.getString("advantage_1"));
            analysis.setAdvantage2(obj.getString("advantage_2"));
            analysis.recommend(obj.getInteger("preferred"), obj.getString("recommendation"));
            
            return analysis;
            
        } catch (Exception e) {
            return new ComparisonAnalysis("resp1", "resp2");
        }
    }
    
    // ========== 降级方法（备用） ==========
    
    private EvaluationResult fallbackEvaluate(String response, String expected) {
        // 使用简单评估引擎作为降级方案
        return new SimpleEvaluationEngine().evaluate(response, expected);
    }
    
    private QualityScore fallbackQualityScore(String response) {
        return new SimpleEvaluationEngine().analyzeQuality(response);
    }
    
    private ComparisonAnalysis fallbackCompare(String response1, String response2) {
        return new SimpleEvaluationEngine().compare(response1, response2);
    }
    
    private EvaluationResult createEmptyResult() {
        EvaluationResult result = new EvaluationResult();
        result.setResponseId("empty");
        result.addIssue("响应为空");
        result.setRecommendation("需要重新生成");
        return result;
    }
    
    private String extractJson(String content) {
        // 提取 ```json ... ``` 之间的内容
        int start = content.indexOf("```json");
        int end = content.indexOf("```", start + 7);
        
        if (start >= 0 && end > start) {
            return content.substring(start + 7, end).trim();
        }
        
        // 如果没有代码块标记，直接返回
        return content.trim();
    }
}