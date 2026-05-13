package com.ck.custom.llmlearn.prompt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prompt模板引擎测试类
 */
class PromptTemplateEngineTest {
    
    private PromptTemplateEngine engine;
    
    @BeforeEach
    void setUp() {
        engine = new PromptTemplateEngine();
        engine.loadFromClasspath("templates/template_demo.yml");
    }
    
    @Test
    void testLoadTemplates() {
        // 验证模板加载成功
        assertTrue(engine.hasTemplate("code_review"));
        assertTrue(engine.hasTemplate("article_writer"));
        assertTrue(engine.hasTemplate("translator"));
        
        System.out.println("✅ 已加载模板数: " + engine.getTemplates().size());
    }
    
    @Test
    void testRenderCodeReviewTemplate() {
        // 准备变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("language", "java");
        variables.put("code", "public class UserController {\n    public void process(String input) {\n        // TODO: SQL injection risk\n    }\n}");
        variables.put("focus_points", "SQL注入风险、XSS漏洞");
        
        // 渲染模板
        RenderedPromptDO prompt = engine.render("code_review", variables);
        
        // 验证渲染结果
        assertNotNull(prompt);
        assertNotNull(prompt.getSystemPrompt());
        assertNotNull(prompt.getUserPrompt());
        
        // 验证变量替换成功
        assertTrue(prompt.getUserPrompt().contains("java"));
        assertTrue(prompt.getUserPrompt().contains("SQL注入风险"));
        assertTrue(prompt.getUserPrompt().contains("UserController"));
        
        System.out.println("===== System Prompt =====");
        System.out.println(prompt.getSystemPrompt());
        System.out.println("\n===== User Prompt =====");
        System.out.println(prompt.getUserPrompt());
        
        System.out.println("✅ code_review 模板渲染成功");
    }
    
    @Test
    void testRenderWithDefaultValues() {
        // 只提供必填变量，其他使用默认值
        Map<String, Object> variables = new HashMap<>();
        variables.put("code", "SELECT * FROM users WHERE id = " + "input");
        
        // 渲染模板（focus_area、standard、language、focus_points 使用默认值）
        RenderedPromptDO prompt = engine.render("code_review", variables);
        
        // 验证默认值生效
        assertTrue(prompt.getSystemPrompt().contains("代码质量、安全性、性能"));
        assertTrue(prompt.getUserPrompt().contains("java"));
        
        System.out.println("✅ 默认值测试成功");
        System.out.println(prompt.getFullPrompt());
    }
    
    @Test
    void testMissingRequiredVariable() {
        // 不提供必填变量 code
        Map<String, Object> variables = new HashMap<>();
        variables.put("language", "java");
        
        // 应该抛出异常
        assertThrows(ValidationException.class, () -> {
            engine.render("code_review", variables);
        });
        
        System.out.println("✅ 必填校验成功（缺少 code 时抛出异常）");
    }
    
    @Test
    void testEnumVariableValidation() {
        // 测试 enum 类型校验
        
        // 有效值
        Map<String, Object> validVars = new HashMap<>();
        validVars.put("language", "python");
        validVars.put("code", "def foo(): pass");
        
        RenderedPromptDO prompt = engine.render("code_review", validVars);
        assertTrue(prompt.getUserPrompt().contains("python"));
        System.out.println("✅ enum有效值测试成功");
        
        // 无效值（不在options中）
        Map<String, Object> invalidVars = new HashMap<>();
        invalidVars.put("language", "ruby");  // ruby不在options中
        invalidVars.put("code", "def foo");
        
        assertThrows(ValidationException.class, () -> {
            engine.render("code_review", invalidVars);
        });
        
        System.out.println("✅ enum无效值校验成功");
    }
    
    @Test
    void testTranslatorTemplate() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("source_language", "英文");
        variables.put("target_language", "中文");
        variables.put("text", "Hello, World! This is a test message.");
        
        RenderedPromptDO prompt = engine.render("translator", variables);
        
        assertTrue(prompt.getSystemPrompt().contains("英文"));
        assertTrue(prompt.getSystemPrompt().contains("中文"));
        assertTrue(prompt.getUserPrompt().contains("Hello, World!"));
        
        System.out.println("✅ translator 模板渲染成功");
        System.out.println(prompt.getFullPrompt());
    }
    
    @Test
    void testArticleWriterTemplate() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("topic", "Prompt模板管理系统设计与实现");
        variables.put("core_content", "Java实现Prompt模板引擎，支持变量注入、校验、版本管理");
        variables.put("word_count", 3000);
        variables.put("target_audience", "中级开发者");
        
        RenderedPromptDO prompt = engine.render("article_writer", variables);
        
        assertTrue(prompt.getUserPrompt().contains("Prompt模板管理系统"));
        assertTrue(prompt.getUserPrompt().contains("3000"));
        
        System.out.println("✅ article_writer 模板渲染成功");
        System.out.println(prompt.getFullPrompt());
    }
}