package com.ck.custom.llmlearn.structured_output;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

/**
 * Week7 Day2 - Demo1: AiServices自动推断结构化输出
 * 
 * 运行方式：直接跑main方法
 * 
 * 核心原理：
 * 1. 你定义接口，方法返回类型是POJO（BookReview）
 * 2. AiServices自动从BookReview类推断出JSON Schema
 * 3. Schema被注入到LLM请求中（不需要你手动拼Prompt）
 * 4. LLM输出JSON → AiServices自动反序列化成BookReview对象
 * 5. 你调用接口方法，直接拿到Java对象——零手动JSON处理
 */
public class StructuredOutputDemo1 {

    public static void main(String[] args) {
        // 1. 创建ChatModel（用SiliconFlow的OpenAI兼容API）
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-kemgxaacaqdehipgoywavavmpaiazkocfjyixcvsnslmeycv")
                .baseUrl("https://api.siliconflow.cn/v1")
                .modelName("Pro/zai-org/GLM-5.1")
                .build();

        // 2. 用AiServices创建BookReviewExtractor代理
        //    这里是魔法：AiServices扫描接口定义
        //    发现extractReview方法返回BookReview类型
        //    → 自动生成BookReview的JSON Schema
        //    → 自动在请求中注入Schema约束
        //    → 自动将LLM输出反序列化成BookReview对象
        BookReviewExtractor extractor = AiServices.create(BookReviewExtractor.class, chatModel);

        // 3. 测试1：一段自由文本书评 → 结构化BookReview对象
        String reviewText1 = """
                《深入理解Java虚拟机》这本书真的是JVM领域的经典之作。
                作者周志明把类加载机制、内存模型、垃圾收集这些底层原理讲得非常透彻，
                每个概念都有实际案例佐证。唯一的缺点是第三版有些章节更新不够及时，
                部分内容还停留在JDK7时代。总体评分8分。
                """;

        BookReview review1 = extractor.extractReview(reviewText1);
        System.out.println("===== 测试1：JVM书评 =====");
        System.out.println("书名: " + review1.getTitle());
        System.out.println("评分: " + review1.getRating());
        System.out.println("总结: " + review1.getSummary());
        System.out.println("优点: " + review1.getPros());
        System.out.println("缺点: " + review1.getCons());

        // 4. 测试2：另一段书评
        String reviewText2 = """
                《三体》这部科幻小说太震撼了，刘慈欣的想象力真的让人叹服。
                黑暗森林法则和降维攻击的概念堪称科幻史上最天才的设定。
                不过人物塑造略显单薄，叶文洁的动机交代不够充分。
                给9分。
                """;

        BookReview review2 = extractor.extractReview(reviewText2);
        System.out.println("\n===== 测试2：三体书评 =====");
        System.out.println("书名: " + review2.getTitle());
        System.out.println("评分: " + review2.getRating());
        System.out.println("总结: " + review2.getSummary());
        System.out.println("优点: " + review2.getPros());
        System.out.println("缺点: " + review2.getCons());

        // 5. 测试3：故意给模糊文本，看LLM怎么推断
        String reviewText3 = """
                最近看了一本关于设计模式的书，写得还行吧，
                有些例子比较实用，但整体感觉太理论了，
                代码示例太少。大概给个6分吧。
                """;

        BookReview review3 = extractor.extractReview(reviewText3);
        System.out.println("\n===== 测试3：模糊书评 =====");
        System.out.println("书名: " + review3.getTitle());
        System.out.println("评分: " + review3.getRating());
        System.out.println("总结: " + review3.getSummary());
        System.out.println("优点: " + review3.getPros());
        System.out.println("缺点: " + review3.getCons());
    }
}
