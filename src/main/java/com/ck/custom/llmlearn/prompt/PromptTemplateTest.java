package com.ck.custom.llmlearn.prompt;

import java.util.HashMap;
import java.util.Map;

/**
 * @author changkong
 * @date 2026/4/30 00:13
 **/
public class PromptTemplateTest {

    public static void main(String[] args) {
        // ========== Step 1: 初始化引擎并加载模板 ==========
        PromptTemplateEngine engine = new PromptTemplateEngine();
        engine.loadFromClasspath("templates/template_demo.yml");

        System.out.println("已加载模板: " + engine.getTemplates().keySet());

        // ========== Step 2: 准备变量并渲染模板 ==========
        Map<String, Object> variables = new HashMap<>();
        variables.put("focus_area", "安全性");
        variables.put("standard", "OWASP安全规范");
        variables.put("language", "java");
        variables.put("code", """
            public class UserController {
                public User getUser(String userId) {
                    String sql = "SELECT * FROM users WHERE id = " + userId;
                    return jdbcTemplate.queryForObject(sql, User.class);
                }
            }
            """);
        variables.put("focus_points", "SQL注入漏洞、敏感信息泄露");

        RenderedPromptDO renderedPrompt = engine.render("code_review", variables);

        System.out.println("\n===== System Prompt =====");
        System.out.println(renderedPrompt.getSystemPrompt());

        System.out.println("\n===== User Prompt =====");
        System.out.println(renderedPrompt.getUserPrompt());

        // ========== Step 3: 调用LLM（可选，需要API Key） ==========
        // 如果有OpenAI API Key，可以取消注释以下代码

        // String apiKey = System.getenv("OPENAI_API_KEY");
        // if (apiKey != null) {
        //     ChatLanguageModel model = OpenAiChatModel.builder()
        //         .apiKey(apiKey)
        //         .modelName("gpt-4")
        //         .temperature(0.7)
        //         .maxTokens(2000)
        //         .build();
        //
        //     // 构造消息列表
        //     List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        //     messages.add(SystemMessage.from(renderedPrompt.getSystemPrompt()));
        //     messages.add(UserMessage.from(renderedPrompt.getUserPrompt()));
        //
        //     // 调用LLM
        //     AiMessage response = model.generate(messages).content();
        //
        //     System.out.println("\n===== LLM Response =====");
        //     System.out.println(response.text());
        // } else {
        //     System.out.println("\n提示: 设置环境变量 OPENAI_API_KEY 以调用LLM");
        // }

        System.out.println("\n✅ 模板渲染完成！");

        // ========== 其他模板示例 ==========

        // 翻译模板
        Map<String, Object> transVars = new HashMap<>();
        transVars.put("source_language", "英文");
        transVars.put("target_language", "中文");
        transVars.put("text", "Hello, this is a test message for translation.");

        RenderedPromptDO transPrompt = engine.render("translator", transVars);
        System.out.println("\n===== 翻译模板示例 =====");
        System.out.println(transPrompt.getFullPrompt());

        // 文章写作模板（使用默认值）
        Map<String, Object> articleVars = new HashMap<>();
        articleVars.put("topic", "RAG系统架构设计");
        articleVars.put("core_content", "RAG原理、向量检索、文档切分、重排序");
        articleVars.put("reference_points", "LangChain4j官方文档、阿里云向量检索服务");

        RenderedPromptDO articlePrompt = engine.render("article_writer", articleVars);
        System.out.println("\n===== 文章写作模板示例 =====");
        System.out.println(articlePrompt.getFullPrompt());
    }
}
