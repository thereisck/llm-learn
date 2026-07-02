package com.ck.custom.llmlearn.integrated;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.util.ArrayList;
import java.util.List;

/**
 * Week7 Day7 - Step1: 上下文管理节点
 *
 * 包装 Day3 的 SlidingWindowDemo，管理多轮对话历史。
 * 管道在调用 LLM 之前，把历史消息和当前输入组装好。
 *
 * @author changkong
 * @date 2026/7/2
 */
public class ContextManagerNode implements PipelineNode {

    /** 滑动窗口，保留最近N条消息 */
    private final ChatMemory memory;
    /** 系统提示词 */
    private final String systemPrompt;

    public ContextManagerNode(int maxMessages, String systemPrompt) {
        this.memory = MessageWindowChatMemory.builder()
                .maxMessages(maxMessages)
                .build();
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String getName() {
        return "ContextManager（上下文管理）";
    }

    /**
     * 把用户输入加入记忆，但不调用LLM（LLM调用由 LlmCallNode 负责）
     */
    @Override
    public void process(ChatContext ctx) {
        // 第一轮加入系统提示
        if (memory.messages().isEmpty() && systemPrompt != null) {
            memory.add(SystemMessage.from(systemPrompt));
        }

        // 用户消息加入记忆
        memory.add(UserMessage.from(ctx.userInput));
        ctx.turnNumber = (memory.messages().size() - (systemPrompt != null ? 1 : 0)) / 2 + 1;

        System.out.println("  [ContextManager] 轮次 #" + ctx.turnNumber +
                " | 记忆中消息数: " + memory.messages().size());
    }

    /**
     * 获取当前所有消息（供 LlmCallNode 使用）
     */
    public List<ChatMessage> getMessages() {
        return memory.messages();
    }

    /**
     * 把AI回复加入记忆（LLM调用后由管道调用）
     */
    public void addAiResponse(String response) {
        memory.add(AiMessage.from(response));
    }

    /**
     * 清空记忆
     */
    public void clearMemory() {
        // 重新创建（ChatMemory 没有clear方法）
        // MessageWindowChatMemory 内部是List，直接重建
        memory.clear();
    }
}
