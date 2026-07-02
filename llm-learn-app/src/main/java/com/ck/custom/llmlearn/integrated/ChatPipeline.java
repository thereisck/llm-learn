package com.ck.custom.llmlearn.integrated;

import com.ck.custom.llmlearn.observability.ProductionTokenTracker;

import java.util.ArrayList;
import java.util.List;

/**
 * Week7 Day7 - Step1: Agent ChatBot 管道主体
 *
 * 把 Day2~Day6 所有模块串成一条完整的责任链：
 *
 *   用户输入
 *     → InputGuardNode      (Day5 安全检查)
 *     → ModelRouterNode     (Day4 模型路由)
 *     → CacheNode           (Day4 请求缓存)
 *     → ContextManagerNode  (Day3 上下文管理)
 *     → LlmCallNode         (LLM调用)
 *     → OutputGuardNode     (Day5 输出审查)
 *     → TokenTrackerNode    (Day6 成本追踪)
 *     → 返回用户
 *
 * 设计模式：
 * - 责任链：每个节点处理完传给下一个
 * - 装饰器：CachedChatModel 透明包装 ChatModel
 * - 策略：ModelRouter 按任务类型选模型
 * - 模板方法：Pipeline.execute() 定义骨架，子节点填充步骤
 *
 * @author changkong
 * @date 2026/7/2
 */
public class ChatPipeline {

    private final List<PipelineNode> nodes = new ArrayList<>();
    private final InputGuardNode inputGuardNode;
    private final ModelRouterNode modelRouterNode;
    public final CacheNode cacheNode;
    private final ContextManagerNode contextManagerNode;
    private final LlmCallNode llmCallNode;
    private final OutputGuardNode outputGuardNode;
    private final TokenTrackerNode tokenTrackerNode;

    /**
     * 构建完整管道
     *
     * @param systemPrompt 系统提示词
     * @param tracker      Token追踪器（需要提前创建，因为要注册到ChatModel上）
     */
    public ChatPipeline(String systemPrompt, ProductionTokenTracker tracker) {
        // 创建所有节点
        this.inputGuardNode = new InputGuardNode();
        this.modelRouterNode = new ModelRouterNode();
        this.cacheNode = new CacheNode(300_000); // 5分钟TTL
        this.contextManagerNode = new ContextManagerNode(10, systemPrompt); // 窗口10条≈5轮
        this.llmCallNode = new LlmCallNode(contextManagerNode, tracker);
        this.outputGuardNode = new OutputGuardNode();
        this.tokenTrackerNode = new TokenTrackerNode(tracker);

        // 按顺序加入管道
        nodes.add(inputGuardNode);
        nodes.add(modelRouterNode);
        nodes.add(cacheNode);
        nodes.add(contextManagerNode);
        nodes.add(llmCallNode);
        nodes.add(outputGuardNode);
        nodes.add(tokenTrackerNode);
    }

    /**
     * 执行管道——核心方法
     *
     * 用户输入 → 依次过每个节点 → 返回最终回复
     */
    public ChatContext execute(String userInput) {
        ChatContext ctx = new ChatContext(userInput);

        System.out.println("\n" + "═".repeat(60));
        System.out.println("🤖 Agent ChatBot Pipeline 启动");
        System.out.println("📝 用户输入: " + truncate(userInput, 50));
        System.out.println("═".repeat(60));

        for (PipelineNode node : nodes) {
            if (node.shouldSkip(ctx)) {
                System.out.println("  [" + node.getName() + "] ⏭️ 跳过");
                continue;
            }

            System.out.println("\n── " + node.getName() + " ──");
            node.process(ctx);
        }

        // 如果缓存命中，需要把AI回复加入上下文
        if (ctx.cacheHit) {
            contextManagerNode.addAiResponse(ctx.llmResponse);
        }

        // 如果LLM被调用了且未命中缓存，把结果写入缓存
        if (!ctx.cacheHit && !ctx.inputBlocked && !ctx.llmResponse.isEmpty()) {
            cacheNode.put(ctx.userInput, ctx.selectedModel, ctx.llmResponse, ctx.latencyMs);
        }

        // 如果输入被拦截，也要记录
        long totalTime = System.currentTimeMillis() - ctx.requestTimestamp;
        System.out.println("\n" + "═".repeat(60));
        System.out.println("✅ 管道完成 | 总耗时: " + totalTime + "ms");
        System.out.println("📤 最终回复: " + truncate(ctx.finalResponse, 100));
        System.out.println("═".repeat(60));

        return ctx;
    }

    /**
     * 打印管道架构
     */
    public void printArchitecture() {
        System.out.println("\n" + "═".repeat(60));
        System.out.println("🏗️ Agent ChatBot 管道架构");
        System.out.println("═".repeat(60));
        System.out.println("用户输入");
        for (PipelineNode node : nodes) {
            System.out.println("  → " + node.getName());
        }
        System.out.println("返回用户");
        System.out.println("═".repeat(60));
    }

    /**
     * 打印所有统计信息
     */
    public void printStats() {
        System.out.println("\n📊 === 管道统计 ===");
        cacheNode.printStats();
        System.out.println("  [Token] " + tokenTrackerNode.getTracker().summary());
        System.out.println("  [Memory] 消息数: " + contextManagerNode.getMessages().size());
    }

    /**
     * 获取Token追踪器（外部调用生成报告）
     */
    public ProductionTokenTracker getTracker() {
        return tokenTrackerNode.getTracker();
    }

    /**
     * 清空缓存和记忆（用于测试场景切换）
     */
    public void reset() {
        cacheNode.clear();
        contextManagerNode.clearMemory();
        tokenTrackerNode.getTracker().reset();
    }

    private String truncate(String s, int max) {
        if (s == null) return "[null]";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
