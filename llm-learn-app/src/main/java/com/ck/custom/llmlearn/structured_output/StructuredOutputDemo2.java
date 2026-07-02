package com.ck.custom.llmlearn.structured_output;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;

/**
 * Week7 Day2 - Demo2: 低级API手动构建JSON Schema
 * 
 * 与Demo1的对比：
 * - Demo1: AiServices自动推断Schema → 简洁但不灵活
 * - Demo2: 手动构建JsonSchema → 更灵活，能精确控制每个字段
 * 
 * 适用场景：
 * - 需要精确控制Schema（枚举、描述、嵌套对象）
 * - 需要使用JsonAnyOf/JsonReference等高级Schema特性
 * - 不想用AiServices代理，想要更透明的控制
 */
public class StructuredOutputDemo2 {

    public static void main(String[] args) throws Exception {
        // 1. 创建ChatModel（增加timeout，手动Schema比AiServices更复杂→LLM推理更久）
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-kemgxaacaqdehipgoywavavmpaiazkocfjyixcvsnslmeycv")
                .baseUrl("https://api.siliconflow.cn/v1")
                .modelName("Pro/zai-org/GLM-5.1")
                .timeout(Duration.ofSeconds(120))  // 默认太短会超时
                .build();

        // 2. 手动构建JSON Schema（这是核心——你精确控制每个字段）
        //    对比Demo1：Demo1让AiServices自动推断，你看不到Schema细节
        //    Demo2让你精确定义每个字段的类型、描述、是否required
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("BookReview")
                .rootElement(JsonObjectSchema.builder()
                        .addProperty("title", JsonStringSchema.builder()
                                .description("书名")
                                .build())
                        .addProperty("rating", JsonIntegerSchema.builder()
                                .description("评分1-10")
                                .build())
                        .addProperty("summary", JsonStringSchema.builder()
                                .description("一句话总结这本书")
                                .build())
                        .addProperty("pros", JsonArraySchema.builder()
                                .description("这本书的优点列表")
                                .items(JsonStringSchema.builder()
                                        .description("一条优点")
                                        .build())
                                .build())
                        .addProperty("cons", JsonArraySchema.builder()
                                .description("这本书的缺点列表")
                                .items(JsonStringSchema.builder()
                                        .description("一条缺点")
                                        .build())
                                .build())
                        // ⚠️ required字段必须显式声明！否则LLM认为所有字段optional，可能省略
                        // 实测教训：第一次只required了title/rating/summary → LLM省略了pros/cons → 返回null
                        .required("title", "rating", "summary", "pros", "cons")
                        .build())
                .build();

        // 3. 构建ResponseFormat（引擎级约束——比Prompt级约束靠谱10倍）
        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(ResponseFormatType.JSON)  // 告诉LLM必须输出JSON
                .jsonSchema(jsonSchema)         // 告诉LLM必须遵守这个Schema
                .build();

        // 4. 构建请求
        String reviewText = """
                《代码整洁之道》这本书教会了我很多关于代码质量的理念，
                函数应该短小精悍、命名要有意义、注释不是越多越好。
                但有些观点过于理想化，实际项目中很难完全遵循。
                给7分。
                """;

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from(reviewText))
                .responseFormat(responseFormat)  // 把Schema塞进请求
                .build();

        // 5. 调用LLM
        ChatResponse chatResponse = chatModel.chat(chatRequest);
        String output = chatResponse.aiMessage().text();
        System.out.println("===== LLM原始JSON输出 =====");
        System.out.println(output);

        // 6. 手动反序列化（对比Demo1：Demo1是AiServices自动完成的）
        //    这里你需要自己用Jackson/ObjectMapper来转换
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        BookReview review = mapper.readValue(output, BookReview.class);

        System.out.println("\n===== 反序列化后的BookReview对象 =====");
        System.out.println("书名: " + review.getTitle());
        System.out.println("评分: " + review.getRating());
        System.out.println("总结: " + review.getSummary());
        System.out.println("优点: " + review.getPros());
        System.out.println("缺点: " + review.getCons());

        // 7. 对比总结
        System.out.println("\n===== Demo1 vs Demo2 对比 =====");
        System.out.println("Demo1(AiServices): 3行代码搞定 → 自动推断Schema + 自动反序列化");
        System.out.println("Demo2(手动Schema): 30行代码 → 精确控制Schema + 手动反序列化");
        System.out.println("生产级选择：简单场景用Demo1，需要精确Schema控制用Demo2");
    }
}
