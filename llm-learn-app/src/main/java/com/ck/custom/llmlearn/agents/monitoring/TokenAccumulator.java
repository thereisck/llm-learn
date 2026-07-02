package com.ck.custom.llmlearn.agents.monitoring;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * ChatModel层Token统计Listener
 *
 * ⚠️ 为什么需要这个？
 * LangChain4j agentic beta版有一个已知bug：
 * AgentInvocationHandler.invokeStandaloneAgent() 调用 ListenerNotifierUtil.afterAgentInvocation()
 * 时用的是5参数版本，没有传递chatRequest和chatResponse。
 * 所以 AgentResponse.chatResponse() 永远是 null，Token统计为0。
 *
 * 根因在AgentInvocationHandler源码（github已确认）：
 * afterAgentInvocation(agentListener, standaloneAgenticScope, this, namedArgs, result);
 * → 内部调用 afterAgentInvocation(listener, scope, agent, inputs, output, null, null);
 * → AgentResponse的chatResponse字段为null！
 *
 * 解决方案：绕过agentic模块的bug，直接在ChatModel层拦截Token数据。
 * ChatModelListener.onResponse() 能拿到完整的ChatResponse（含TokenUsage），
 * 这个数据是LLM API直接返回的，不经过agentic模块的处理。
 *
 * 数据流向对比：
 * ❌ 原路径：LLM API → ChatResponse → AgentInvocationHandler → AgentResponse(chatResponse=null) → TraceListener
 * ✅ 新路径：LLM API → ChatResponse → ChatModelListener.onResponse() → TokenAccumulator → TraceListener
 */
@Slf4j
public class TokenAccumulator implements ChatModelListener {

    // 累计Token数（线程安全，因为Agent可能并发调用多个LLM请求）
    private final AtomicInteger totalInputTokens = new AtomicInteger(0);
    private final AtomicInteger totalOutputTokens = new AtomicInteger(0);
    private final AtomicInteger totalTokens = new AtomicInteger(0);

    // 累计LLM调用次数
    private final AtomicInteger llmCallCount = new AtomicInteger(0);

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        // 请求发送前——不需要特别处理
        llmCallCount.incrementAndGet();
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        // ⚠️ 这就是关键！ChatModelListener能拿到完整的ChatResponse
        // 而AgentListener的AgentResponse.chatResponse()是null（agentic bug）
        if (responseContext.chatResponse() != null
                && responseContext.chatResponse().tokenUsage() != null) {
            var tokenUsage = responseContext.chatResponse().tokenUsage();
            int inputTokens = tokenUsage.inputTokenCount();
            int outputTokens = tokenUsage.outputTokenCount();
            int total = tokenUsage.totalTokenCount();

            totalInputTokens.addAndGet(inputTokens);
            totalOutputTokens.addAndGet(outputTokens);
            totalTokens.addAndGet(total);

            log.info("🔢 [TOKEN] LLM调用#{} | 输入:{} | 输出:{} | 总:{} Token",
                    llmCallCount.get(), inputTokens, outputTokens, total);
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        log.warn("🔢 [TOKEN] LLM调用出错: {}", errorContext.error().getMessage());
    }

    // ========== 统计输出 ==========

    public int getTotalInputTokens() { return totalInputTokens.get(); }
    public int getTotalOutputTokens() { return totalOutputTokens.get(); }
    public int getTotalTokens() { return totalTokens.get(); }
    public int getLlmCallCount() { return llmCallCount.get(); }

    /**
     * 重置所有统计（用于每次测试清空）
     */
    public void reset() {
        totalInputTokens.set(0);
        totalOutputTokens.set(0);
        totalTokens.set(0);
        llmCallCount.set(0);
    }

    /**
     * 生成Token统计摘要
     */
    public String summary() {
        return String.format("LLM调用%d次 | 输入Token:%d | 输出Token:%d | 总Token:%d",
                llmCallCount.get(), totalInputTokens.get(), totalOutputTokens.get(), totalTokens.get());
    }
}
