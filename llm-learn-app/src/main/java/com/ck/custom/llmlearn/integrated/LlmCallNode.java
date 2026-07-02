package com.ck.custom.llmlearn.integrated;

import com.ck.custom.llmlearn.observability.ProductionTokenTracker;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;
import java.util.List;

/**
 * Week7 Day7 - Step2: LLM调用节点（修复Token追踪）
 *
 * 修复内容：创建ChatModel时注册 ProductionTokenTracker Listener
 * 这样每次LLM调用的Token/成本/延迟都会被自动追踪
 *
 * @author changkong
 * @date 2026/7/2
 */
public class LlmCallNode implements PipelineNode {

    private static final String BASE_URL = "https://api.siliconflow.cn/v1";
    private static final String API_KEY = "sk-kemgxaacaqdehipgoywavavmpaiazkocfjyixcvsnslmeycv";

    private final ContextManagerNode contextManager;
    private final ProductionTokenTracker tokenTracker;

    /** 模型缓存：同一个模型名只创建一次 */
    private final java.util.Map<String, ChatModel> modelCache = new java.util.concurrent.ConcurrentHashMap<>();

    public LlmCallNode(ContextManagerNode contextManager, ProductionTokenTracker tokenTracker) {
        this.contextManager = contextManager;
        this.tokenTracker = tokenTracker;
    }

    @Override
    public String getName() {
        return "LLMCall（大模型调用）";
    }

    @Override
    public void process(ChatContext ctx) {
        // 1. 获取或创建带Token追踪的模型
        ChatModel chatModel = modelCache.computeIfAbsent(ctx.selectedModel,
                name -> OpenAiChatModel.builder()
                        .baseUrl(BASE_URL)
                        .apiKey(API_KEY)
                        .modelName(name)
                        .timeout(Duration.ofSeconds(120))
                        .listeners(List.of(tokenTracker))
                        .build());

        // 2. 获取上下文消息（包含历史）
        List<ChatMessage> messages = contextManager.getMessages();

        // 3. 调用LLM
        long start = System.currentTimeMillis();
        System.out.println("  [LLMCall] 调用 " + ctx.selectedModel + "...");

        String response;
        if (messages.size() > 1) {
            // 多轮对话：把完整历史传给模型
            response = chatModel.chat(messages).aiMessage().text();
        } else {
            // 单轮：直接用文本
            response = chatModel.chat(ctx.userInput);
        }

        ctx.latencyMs = System.currentTimeMillis() - start;
        ctx.llmResponse = response;

        System.out.println("  [LLMCall] ✅ 响应 " + response.length() + " 字符 | 耗时 " + ctx.latencyMs + "ms");

        // 4. 把AI回复加入上下文记忆
        contextManager.addAiResponse(response);
    }
}
