package com.ck.custom.llmlearn.prompt_optimizer.controller;

import com.ck.custom.llmlearn.prompt_optimizer.service.*;
import com.ck.custom.llmlearn.prompt_optimizer.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Prompt优化器 REST API控制器
 * 
 * API端点：
 * - GET /api/prompt/templates - 获取模板列表
 * - POST /api/prompt/templates - 注册模板
 * - PUT /api/prompt/templates/{id} - 更新模板
 * - DELETE /api/prompt/templates/{id} - 删除模板
 * - POST /api/prompt/render - 渲染模板
 * - POST /api/prompt/test - 测试单个Prompt
 * - POST /api/prompt/abtest - A/B测试对比
 */
@Slf4j
@RestController
@RequestMapping("/api/prompt")
@CrossOrigin(origins = "*") // 允许前端跨域访问
public class PromptOptimizerController {
    
    @Autowired
    private PromptOptimizerService promptOptimizerService;
    
    // ========== 模板管理 ==========
    
    /**
     * 注册模板
     */
    @PostMapping("/templates")
    public ResponseEntity<PromptTemplateDTO> registerTemplate(@RequestBody PromptTemplateDTO template) {
        log.info("注册模板: {}", template.getName());
        
        PromptTemplateDTO registered = promptOptimizerService.registerTemplate(template);
        
        return ResponseEntity.ok(registered);
    }
    
    /**
     * 获取模板列表
     */
    @GetMapping("/templates")
    public ResponseEntity<List<PromptTemplateDTO>> listTemplates(
        @RequestParam(required = false) String category) {
        
        log.info("查询模板列表: category={}", category);
        
        List<PromptTemplateDTO> templates;
        if (category != null && !category.isEmpty()) {
            templates = promptOptimizerService.listTemplatesByCategory(category);
        } else {
            templates = promptOptimizerService.listTemplates();
        }
        
        return ResponseEntity.ok(templates);
    }
    
    /**
     * 更新模板
     */
    @PutMapping("/templates/{id}")
    public ResponseEntity<PromptTemplateDTO> updateTemplate(
        @PathVariable String id,
        @RequestBody PromptTemplateDTO template) {
        log.info("更新模板: id={}, name={}", id, template.getName());
        
        PromptTemplateDTO updated = promptOptimizerService.updateTemplate(id, template);
        
        return ResponseEntity.ok(updated);
    }
    
    /**
     * 删除模板
     */
    @DeleteMapping("/templates/{id}")
    public ResponseEntity<Map<String, Boolean>> deleteTemplate(@PathVariable String id) {
        log.info("删除模板: id={}", id);
        
        boolean deleted = promptOptimizerService.deleteTemplate(id);
        
        return ResponseEntity.ok(Map.of("success", deleted));
    }
    
    /**
     * 渲染模板
     */
    @PostMapping("/render")
    public ResponseEntity<Map<String, String>> renderTemplate(@RequestBody RenderRequest request) {
        log.info("渲染模板: templateId={}, params={}", request.getTemplateId(), request.getParams());
        
        String rendered = promptOptimizerService.renderTemplate(request.getTemplateId(), request.getParams());
        
        return ResponseEntity.ok(Map.of("prompt", rendered));
    }
    
    // ========== Prompt测试 ==========
    
    /**
     * 测试单个Prompt
     */
    @PostMapping("/test")
    public ResponseEntity<TestResult> testPrompt(@RequestBody TestRequest request) {
        log.info("测试Prompt: {}", request);
        
        TestResult result = promptOptimizerService.testPrompt(request);
        
        return ResponseEntity.ok(result);
    }
    
    // ========== A/B测试 ==========
    
    /**
     * A/B测试对比
     */
    @PostMapping("/abtest")
    public ResponseEntity<ABTestResult> abTest(@RequestBody ABTestRequest request) {
        log.info("A/B测试: {}个方案", request.getPrompts().size());
        
        ABTestResult result = promptOptimizerService.abTest(request);
        
        return ResponseEntity.ok(result);
    }
    
    // ========== 报告导出 ==========
    
    /**
     * 导出Markdown报告
     */
    @GetMapping("/report/{id}")
    public ResponseEntity<Map<String, String>> exportReport(@PathVariable String id) {
        log.info("导出报告: id={}", id);
        
        // TODO: 实现报告存储和查询
        return ResponseEntity.ok(Map.of("report", "报告内容（待实现）"));
    }
}