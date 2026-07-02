package com.ck.custom.llmlearn.integrated;

/**
 * Week7 Day7 - Step1: 管道节点接口
 *
 * 责任链模式的核心——每个节点做一件事，做完传给下一个。
 *
 * 对比 Claude Code 的四阶段权限管线：
 *   解析(Parse) → 校验(Validate) → 执行(Execute) → 审计(Audit)
 * 每个阶段都是独立的，前一个拦截了后面就不执行。
 *
 * 我们的管道：
 *   InputGuard → Cache → ModelRouter → ContextManager → LLMCall → OutputGuard → TokenTracker
 *
 * @author changkong
 * @date 2026/7/2
 */
public interface PipelineNode {

    /**
     * 节点名称（用于日志和调试）
     */
    String getName();

    /**
     * 执行本节点的处理逻辑
     *
     * @param ctx 管道上下文
     */
    void process(ChatContext ctx);

    /**
     * 是否应该跳过本节点（如果上游已拦截，某些节点可以跳过）
     * 默认不跳过，子类按需覆盖
     */
    default boolean shouldSkip(ChatContext ctx) {
        return ctx.shouldStop;
    }
}
