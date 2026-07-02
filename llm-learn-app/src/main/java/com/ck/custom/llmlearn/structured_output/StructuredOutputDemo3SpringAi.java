package com.ck.custom.llmlearn.structured_output;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Week7 Day2 - Step2: Spring AI Structured Output实战（Spring Boot版）
 * 
 * 与LangChain4j对比：
 * - LangChain4j: AiServices接口 → 自动推断Schema → 自动反序列化
 * - Spring AI: BeanOutputConverter → 自动生成Schema指令 → 手动追加到Prompt → 手动反序列化
 *              或 ChatClient.entity() → 一行代码搞定（类似AiServices）
 * 
 * 运行方式：SpringBoot应用启动 → CommandLineRunner自动执行
 */
@SpringBootApplication
public class StructuredOutputDemo3SpringAi implements CommandLineRunner {

    // Spring AI的ChatClient会被Spring Boot自动配置
    // 配置在application.properties里设置openai.api.base-url/model/key
    @Autowired
    private ChatClient.Builder chatClientBuilder;

    public static void main(String[] args) {
        SpringApplication.run(StructuredOutputDemo3SpringAi.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        ChatClient chatClient = chatClientBuilder.build();

        System.out.println("\n===== 方式1：BeanOutputConverter（手动方式，最透明） =====");

        // 1. 创建BeanOutputConverter（自动从POJO生成JSON Schema指令）
        BeanOutputConverter<BookReview> converter = new BeanOutputConverter<>(BookReview.class);

        // 2. 看看Converter生成的Schema指令
        String formatInstructions = converter.getFormat();
        System.out.println("Schema指令长度: " + formatInstructions.length() + " 字符");
        System.out.println("Schema指令前200字符: " + formatInstructions.substring(0, Math.min(200, formatInstructions.length())));

        // 3. 组合Prompt（Spring AI需要手动追加Schema指令）
        String userPrompt = """
                请从以下书评文本中提取书名、评分(1-10)、一句话总结、优点列表和缺点列表。
                
                书评内容：
                《重构：改善既有代码的设计》这本书真的太实用了，
                每个重构手法都有详细的步骤和示例代码。
                不过有些重构手法在实际项目中用得不多，比如"引入参数对象"。
                评分8分。
                """;

        String fullPrompt = userPrompt + "\n" + formatInstructions;

        // 4. 调用ChatClient
        String response = chatClient.prompt()
                .user(fullPrompt)
                .call()
                .content();

        System.out.println("\n===== LLM原始输出 =====");
        System.out.println(response);

        // 5. 反序列化
        BookReview review1 = converter.convert(response);
        System.out.println("\n===== 反序列化后的BookReview对象 =====");
        System.out.println("书名: " + review1.getTitle());
        System.out.println("评分: " + review1.getRating());
        System.out.println("总结: " + review1.getSummary());
        System.out.println("优点: " + review1.getPros());
        System.out.println("缺点: " + review1.getCons());

        System.out.println("\n===== 方式2：ChatClient.entity()（更简洁，类似LangChain4j AiServices） =====");

        // 一行代码搞定！不需要手动生成Schema、不需要手动追加Prompt、不需要手动反序列化
        BookReview review2 = chatClient.prompt()
                .user("请从以下书评中提取结构化信息：《设计模式》这本书经典但有些模式过时了，给6分。")
                .call()
                .entity(BookReview.class);

        System.out.println("书名: " + review2.getTitle());
        System.out.println("评分: " + review2.getRating());
        System.out.println("总结: " + review2.getSummary());
        System.out.println("优点: " + review2.getPros());
        System.out.println("缺点: " + review2.getCons());

        System.out.println("\n===== 三种结构化输出方式对比 =====");
        System.out.println("Demo1(LangChain4j AiServices): 定义接口→3行代码→全自动");
        System.out.println("Demo2(LangChain4j 手动Schema): 30行→精确控制Schema+手动反序列化");
        System.out.println("Demo3-SpringAI方式1(BeanOutputConverter): 手动组合Prompt+手动反序列化=最透明");
        System.out.println("Demo3-SpringAI方式2(ChatClient.entity()): 一行代码→类似AiServices的简洁");
    }
}