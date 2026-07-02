package com.ck.custom.llmlearn.context;

/**
 * @author changkong
 * @date 2026/6/19 16:28
 **/

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.List;

/**
 * Week7 Day3 - Step2: 滑动窗口ChatMemory演示
 *
 * 核心概念：
 * 1. ChatMemory = 对话历史的容器
 * 2. MessageWindowChatMemory = 滑动窗口实现，只保留最近N条消息
 * 3. 窗口满了 → 最老的消息自动被丢弃
 *
 * 运行方式：直接跑main方法
 * 观察重点：
 * - 第3轮对话时，第1轮的内容还在不在？
 * - 窗口大小对LLM"记忆"的影响
 */
public class SlidingWindowDemo {
    public static void main(String[] args) {
        // 1. 创建ChatModel（跟你之前Demo一样的配置）
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-kemgxaacaqdehipgoywavavmpaiazkocfjyixcvsnslmeycv")
                .baseUrl("https://api.siliconflow.cn/v1")
                .modelName("Pro/zai-org/GLM-5.1")
                .build();

        // 2. 创建滑动窗口ChatMemory，窗口大小=4（保留最近4条消息）
        //    为什么是4？因为一轮对话=用户1条+AI1条=2条消息
        //    窗口=4 → 保留最近2轮完整对话
        ChatMemory memory= MessageWindowChatMemory.builder().maxMessages(4).build();
        // 3. 模拟多轮对话，观察窗口滑动效果
        String[] turns = {
                "我叫张空少，是一个Java后端开发者，今年转行做大模型应用",
                "我之前学过RAG系统搭建，用的是LangChain4j",
                "我今天在学习上下文管理，你能记住我叫什么吗？",
                "我是做什么工作的？我之前学了什么？"
        };

        for (int i = 0; i < turns.length; i++) {
            System.out.println("========== 第" + (i + 1) + "轮 ==========");

            // 3.1 用户消息加入memory
            memory.add(UserMessage.from(turns[i]));

            // 3.2 打印当前memory中的所有消息（观察窗口滑动！）
            System.out.println("【当前Memory中的消息】");
            List<ChatMessage> messages = memory.messages();
            for (int j = 0; j < messages.size(); j++) {
                ChatMessage msg = messages.get(j);
                System.out.println("  [" + j + "] " + msg.type() + ": "
                        + truncate(msg.toString(), 60));
            }
            System.out.println("  → 共 " + messages.size() + " 条消息（窗口上限4条）");

            // 3.3 发送给LLM（把memory中所有消息作为上下文）
            String response = chatModel.chat(messages).aiMessage().text();
            System.out.println("用户: " + turns[i]);
            System.out.println("AI: " + response);

            // 3.4 AI回复加入memory
            memory.add(AiMessage.from(response));

            System.out.println();
        }
    }

    /** 截断字符串工具方法 */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
