package com.ck.custom.llmlearn.structured_output;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

/**
 * Week7 Day2 - Step3: LangChain4j 流式输出实战
 * 
 * 两种方式：
 * 1. StreamingChatModel + StreamingChatResponseHandler（低级API，逐token回调）
 * 2. AiServices + TokenStream（高级API，像流一样迭代）
 */
public class StructuredOutputDemo4Streaming {

    // ========== 方式1：低级API - StreamingChatModel ==========

    static void demo1_lowLevelStreaming() throws InterruptedException {
        System.out.println("\n===== Demo1: StreamingChatModel（低级API，逐token回调） =====");

        StreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
                .baseUrl("https://api.siliconflow.cn/v1")
                .apiKey("sk-kemgxaacaqdehipgoywavavmpaiazkocfjyixcvsnslmeycv")
                .modelName("Pro/zai-org/GLM-5.1")
                .timeout(Duration.ofSeconds(60))
                .build();

        // 用CountDownLatch等待流式完成
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder fullResponse = new StringBuilder();

        streamingModel.chat(
                "用三句话解释什么是RAG检索增强生成",
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        // 每收到一个token就打印（这是流式输出的核心）
                        System.out.print(partialResponse);
                        fullResponse.append(partialResponse);
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        // 流式输出完成
                        System.out.println("\n--- 流式完成 ---");
                        System.out.println("完整响应长度: " + fullResponse.length() + " 字符");
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable error) {
                        System.err.println("流式输出错误: " + error.getMessage());
                        latch.countDown();
                    }
                }
        );

        // 等待流式完成（最多等60秒）
        latch.await(60, java.util.concurrent.TimeUnit.SECONDS);
    }

    // ========== 方式2：高级API - AiServices + TokenStream ==========

    interface StreamingAssistant {
        @SystemMessage("你是一个简洁的技术专家，回答要精炼但准确。")
        @UserMessage("{{question}}")
        TokenStream chat(String question);
    }

    static void demo2_aiServicesStreaming() throws InterruptedException {
        System.out.println("\n===== Demo2: AiServices + TokenStream（高级API，像流一样迭代） =====");

        StreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
                .baseUrl("https://api.siliconflow.cn/v1")
                .apiKey("sk-kemgxaacaqdehipgoywavavmpaiazkocfjyixcvsnslmeycv")
                .modelName("Pro/zai-org/GLM-5.1")
                .timeout(Duration.ofSeconds(60))
                .build();

        // AiServices自动创建流式代理
        StreamingAssistant assistant = AiServices.create(StreamingAssistant.class, streamingModel);

        // 获取TokenStream
        TokenStream tokenStream = assistant.chat("用三句话解释什么是Agent智能体");

        // TokenStream有两种消费方式：

        // 方式A：逐token回调（适合SSE推送给前端）
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder fullResponse = new StringBuilder();

        tokenStream
                .onPartialResponse(token -> {
                    System.out.print(token);
                    fullResponse.append(token);
                })
                .onCompleteResponse(response -> {
                    System.out.println("\n--- TokenStream完成 ---");
                    System.out.println("完整响应: " + fullResponse.toString());
                    latch.countDown();
                })
                .onError(error -> {
                    System.err.println("TokenStream错误: " + error.getMessage());
                    latch.countDown();
                })
                .start();  // 必须调用start()才开始流式输出

        latch.await(60, java.util.concurrent.TimeUnit.SECONDS);
    }

    // ========== 方式3：流式 + 结构化输出组合 ==========

    interface StreamingBookReviewExtractor {
        @SystemMessage("你是一个书评分析专家，从书评文本中提取结构化信息。")
        @UserMessage("请从以下书评中提取书名、评分、总结、优点和缺点：{{review}}")
        TokenStream extractReview(String review);
    }

    static void demo3_streamingStructuredOutput() throws InterruptedException {
        System.out.println("\n===== Demo3: 流式 + 结构化输出组合 =====");
        System.out.println("注意：流式输出拿到的是JSON字符串的逐token片段");
        System.out.println("需要在 onCompleteResponse 中解析完整JSON才能得到结构化对象");

        StreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
                .baseUrl("https://api.siliconflow.cn/v1")
                .apiKey(System.getenv("SILICONFLOW_API_KEY"))
                .modelName("Pro/zai-org/GLM-5.1")
                .timeout(Duration.ofSeconds(60))
                .build();

        // AiServices返回类型是BookReview（结构化），但用TokenStream拿到的是JSON片段
        // 方案：用低级API + 手动反序列化
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder jsonBuilder = new StringBuilder();

        streamingModel.chat(
                "请从以下书评中提取JSON格式的书名(title)、评分(rating,1-10整数)、一句话总结(summary)、优点列表(pros)、缺点列表(cons)：\n"
                + "《Effective Java》这本书每个Java开发者都该读，给9分。优点是编程规范清晰、案例丰富，缺点是部分内容已过时。",
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        // 流式逐token输出JSON片段
                        System.out.print(partialResponse);
                        jsonBuilder.append(partialResponse);
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        System.out.println("\n--- 流式JSON输出完成 ---");
                        String fullJson = jsonBuilder.toString();
                        System.out.println("完整JSON: " + fullJson);

                        // 流式完成后手动解析JSON → 结构化对象
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            BookReview review = mapper.readValue(fullJson, BookReview.class);
                            System.out.println("✅ 反序列化成功！");
                            System.out.println("书名: " + review.getTitle());
                            System.out.println("评分: " + review.getRating());
                            System.out.println("总结: " + review.getSummary());
                            System.out.println("优点: " + review.getPros());
                            System.out.println("缺点: " + review.getCons());
                        } catch (Exception e) {
                            System.out.println("❌ JSON解析失败: " + e.getMessage());
                            System.out.println("原因: 流式输出的JSON可能包含markdown标记或多余字符");
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable error) {
                        System.err.println("流式错误: " + error.getMessage());
                        latch.countDown();
                    }
                }
        );

        latch.await(60, java.util.concurrent.TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws InterruptedException {
        String apiKey = "sk-kemgxaacaqdehipgoywavavmpaiazkocfjyixcvsnslmeycv";
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("请设置环境变量 SILICONFLOW_API_KEY");
            return;
        }

        demo1_lowLevelStreaming();
        demo2_aiServicesStreaming();
        demo3_streamingStructuredOutput();

        System.out.println("\n===== Step3 总结 =====");
        System.out.println("StreamingChatModel: 低级API，逐token回调，适合SSE推前端");
        System.out.println("AiServices+TokenStream: 高级API，onPartialResponse/onComplete/onError三回调");
        System.out.println("流式+结构化: 流式拿到JSON片段→完成后手动解析→结构化对象");
    }
}
