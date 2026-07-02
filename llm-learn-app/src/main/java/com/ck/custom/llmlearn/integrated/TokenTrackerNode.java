package com.ck.custom.llmlearn.integrated;

import com.ck.custom.llmlearn.observability.ProductionTokenTracker;

/**
 * Week7 Day7 - Step1: Token追踪节点
 *
 * 包装 Day6 的 ProductionTokenTracker。
 * 这个节点比较特殊——它不是在管道中间处理，而是在 LLM 调用时通过 Listener 自动收集。
 * 这里作为管道的最后一个节点，负责输出统计报告。
 *
 * @author changkong
 * @date 2026/7/2
 */
public class TokenTrackerNode implements PipelineNode {

    private final ProductionTokenTracker tracker;

    public TokenTrackerNode(ProductionTokenTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public String getName() {
        return "TokenTracker（成本追踪）";
    }

    @Override
    public void process(ChatContext ctx) {
        // Token 已经通过 Listener 自动收集了，这里只输出单轮摘要
        System.out.println("  [TokenTracker] " + tracker.summary());
    }

    public ProductionTokenTracker getTracker() {
        return tracker;
    }
}
