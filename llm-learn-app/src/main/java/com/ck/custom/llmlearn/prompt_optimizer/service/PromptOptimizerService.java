package com.ck.custom.llmlearn.prompt_optimizer.service;

import com.ck.custom.llmlearn.prompt_optimizer.client.*;
import com.ck.custom.llmlearn.prompt_optimizer.engine.*;
import com.ck.custom.llmlearn.prompt_optimizer.manager.*;
import com.ck.custom.llmlearn.prompt_optimizer.model.*;
import com.ck.custom.llmlearn.prompt_optimizer.report.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Prompt优化器核心服务
 * 
 * 整合所有模块，提供完整的优化流程：
 * 1. 模板管理（注册、查询、渲染）
 * 2. Prompt测试（单次调用 → LLM评估 → 报告生成）
 * 3. A/B测试（多Prompt对比 → 评估 → 推荐最优）
 */
@Slf4j
@Service
public class PromptOptimizerService {
    
    @Autowired
    private PromptTemplateManager templateManager;
    
    @Autowired
    private SiliconFlowLLMClient llmClient; // SiliconFlow客户端（OpenAI兼容）
    
    @Autowired
    private LLMEvaluationEngine llmEvaluationEngine; // LLMEvaluationEngine
    
    @Autowired
    private ComparisonReport comparisonReport;
    
    // ========== 模板管理 ==========
    
    /**
     * 注册模板
     */
    public PromptTemplateDTO registerTemplate(PromptTemplateDTO template) {
        return templateManager.register(template);
    }
    
    /**
     * 渲染模板
     */
    public String renderTemplate(String templateId, Map<String, String> params) {
        return templateManager.render(templateId, params);
    }
    
    /**
     * 获取所有模板
     */
    public List<PromptTemplateDTO> listTemplates() {
        return templateManager.listAll();
    }
    
    /**
     * 按分类查询模板
     */
    public List<PromptTemplateDTO> listTemplatesByCategory(String category) {
        return templateManager.listByCategory(category);
    }
    
    /**
     * 更新模板
     */
    public PromptTemplateDTO updateTemplate(String templateId, PromptTemplateDTO template) {
        return templateManager.update(templateId, template);
    }
    
    /**
     * 删除模板
     */
    public boolean deleteTemplate(String templateId) {
        return templateManager.delete(templateId);
    }
    
    // ========== Prompt测试 ==========
    
    /**
     * 测试单个Prompt
     * 
     * 流程：
     * 1. 渲染模板（如果提供templateId）
     * 2. 调用LLM
     * 3. LLM评估输出质量
     * 4. 生成报告
     */
    public TestResult testPrompt(TestRequest request) {
        log.info("开始测试Prompt: {}", request);
        
        long startTime = System.currentTimeMillis();
        
        // 1. 获取最终Prompt
        String finalPrompt = getFinalPrompt(request);
        
        // 2. 调用LLM
        LLMConfig config = request.getConfig() != null ? request.getConfig() : LLMConfig.defaultConfig();
        LLMResponse llmResponse = llmClient.call(finalPrompt, config);
        
        // 3. LLM评估
        EvaluationResult evaluationResult = llmEvaluationEngine.evaluate(
            llmResponse.getContent(), 
            request.getExpectedOutput()
        );
        
        // 4. 生成报告
        String report = generateTestReport(finalPrompt, llmResponse, evaluationResult);
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        return TestResult.builder()
            .prompt(finalPrompt)
            .response(llmResponse.getContent())
            .tokenUsage(llmResponse.getTokenUsage())
            .latencyMs(llmResponse.getLatencyMs())
            .evaluation(evaluationResult)
            .report(report)
            .totalTimeMs(totalTime)
            .success(llmResponse.isSuccess())
            .build();
    }
    
    // ========== A/B测试 ==========
    
    /**
     * A/B测试：对比多个Prompt方案
     * 
     * 流程：
     * 1. 渲染所有模板
     * 2. 并行调用LLM
     * 3. 并行评估
     * 4. 生成对比报告 + 推荐
     */
    public ABTestResult abTest(ABTestRequest request) {
        log.info("开始A/B测试: {} 个方案", request.getPrompts().size());
        
        long startTime = System.currentTimeMillis();
        
        // 1. 准备所有Prompt
        List<String> prompts = request.getPrompts().stream()
            .map(this::getFinalPromptFromABTest)
            .collect(Collectors.toList());
        
        // 2. 并行调用LLM
        LLMConfig config = request.getConfig() != null ? request.getConfig() : LLMConfig.defaultConfig();
        List<LLMResponse> responses = llmClient.batchCall(prompts, config);
        
        // 3. 并行评估
        List<EvaluationResult> evaluations = llmEvaluationEngine.batchEvaluate(
            responses.stream().map(LLMResponse::getContent).collect(Collectors.toList()),
            request.getExpectedOutput()
        );
        
        // 4. 生成对比报告
        List<TokenUsage> usages = responses.stream()
            .map(LLMResponse::getTokenUsage)
            .collect(Collectors.toList());
        
        String comparisonReportStr = comparisonReport.exportFullReport(evaluations, usages);
        
        // 5. 推荐最优方案
        Recommendation recommendation = comparisonReport.recommendBest(evaluations);
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        // 6. 构建详细结果
        List<ABTestResult.ABTestDetail> details = buildABTestDetails(prompts, responses, evaluations);
        
        return ABTestResult.builder()
            .details(details)
            .comparisonReport(comparisonReportStr)
            .recommendation(recommendation)
            .costAnalysis(comparisonReport.analyzeCost(usages))
            .totalTimeMs(totalTime)
            .build();
    }
    
    // ========== 辅助方法 ==========
    
    private String getFinalPrompt(TestRequest request) {
        if (request.getTemplateId() != null && !request.getTemplateId().isEmpty()) {
            // 使用模板渲染
            return templateManager.render(request.getTemplateId(), request.getParams());
        } else if (request.getPrompt() != null && !request.getPrompt().isEmpty()) {
            // 直接使用Prompt
            return request.getPrompt();
        } else {
            throw new IllegalArgumentException("必须提供templateId或prompt");
        }
    }
    
    private String getFinalPromptFromABTest(ABTestRequest.ABTestPromptItem item) {
        if (item.getTemplateId() != null && !item.getTemplateId().isEmpty()) {
            return templateManager.render(item.getTemplateId(), item.getParams());
        } else {
            return item.getPrompt();
        }
    }
    
    private String generateTestReport(String prompt, LLMResponse response, EvaluationResult evaluation) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("# Prompt测试报告\n\n");
        sb.append(String.format("生成时间：%s\n\n", new Date()));
        
        sb.append("## Prompt内容\n\n");
        sb.append(prompt).append("\n\n");
        
        sb.append("## LLM响应\n\n");
        sb.append(response.getContent()).append("\n\n");
        
        sb.append("## Token消耗\n\n");
        sb.append(String.format("- 输入Token: %d\n", response.getTokenUsage().getInputTokens()));
        sb.append(String.format("- 输出Token: %d\n", response.getTokenUsage().getOutputTokens()));
        sb.append(String.format("- 总Token: %d\n", response.getTokenUsage().getTotalTokens()));
        sb.append(String.format("- 成本: $%.4f (≈¥%.3f)\n", 
            response.getTokenUsage().calculateCostUSD(),
            response.getTokenUsage().calculateCostCNY()));
        sb.append(String.format("- 响应延迟: %dms\n", response.getLatencyMs()));
        sb.append("\n");
        
        sb.append("## 质量评估\n\n");
        sb.append(String.format("- 准确性: %.2f\n", evaluation.getAccuracyScore()));
        sb.append(String.format("- 完整度: %.2f\n", evaluation.getCompletenessScore()));
        sb.append(String.format("- 格式评分: %.2f\n", evaluation.getFormatScore()));
        sb.append(String.format("- 综合评分: %.2f (%s)\n\n", 
            evaluation.getOverallScore(), evaluation.getGrade()));
        
        if (!evaluation.getStrengths().isEmpty()) {
            sb.append("### 优点\n\n");
            for (String strength : evaluation.getStrengths()) {
                sb.append(String.format("- %s\n", strength));
            }
            sb.append("\n");
        }
        
        if (!evaluation.getIssues().isEmpty()) {
            sb.append("### 问题\n\n");
            for (String issue : evaluation.getIssues()) {
                sb.append(String.format("- %s\n", issue));
            }
            sb.append("\n");
        }
        
        sb.append(String.format("### 建议\n\n%s\n", evaluation.getRecommendation()));
        
        return sb.toString();
    }
    
    private List<ABTestResult.ABTestDetail> buildABTestDetails(List<String> prompts, List<LLMResponse> responses, List<EvaluationResult> evaluations) {
        List<ABTestResult.ABTestDetail> details = new ArrayList<>();
        
        for (int i = 0; i < prompts.size(); i++) {
            details.add(ABTestResult.ABTestDetail.builder()
                .index(i + 1)
                .prompt(prompts.get(i))
                .response(responses.get(i).getContent())
                .tokenUsage(responses.get(i).getTokenUsage())
                .latencyMs(responses.get(i).getLatencyMs())
                .evaluation(evaluations.get(i))
                .build());
        }
        
        return details;
    }
}