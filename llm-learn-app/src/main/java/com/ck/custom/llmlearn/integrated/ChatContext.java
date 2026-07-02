package com.ck.custom.llmlearn.integrated;

/**
 * Week7 Day7 - Step1: 聊天管道上下文
 *
 * 管道中每个节点都能读写的"货物"。
 * 用户输入 → InputGuard检查 → 缓存判断 → 模型路由 → 上下文管理 → LLM调用 → OutputGuard审查 → Token追踪 → 返回
 * 所有数据都装在这个对象里传递。
 *
 * @author changkong
 * @date 2026/7/2
 */
public class ChatContext {

    // ========== 输入 ==========
    /** 用户原始输入 */
    public String userInput;

    // ========== 安全检查 ==========
    /** 是否被InputGuard拦截 */
    public boolean inputBlocked = false;
    /** 拦截原因 */
    public String inputBlockReason = "";

    // ========== 缓存 ==========
    /** 是否命中缓存 */
    public boolean cacheHit = false;

    // ========== 模型路由 ==========
    /** 选中的模型名 */
    public String selectedModel = "";
    /** 任务类型 */
    public String taskType = "CHAT";

    // ========== 上下文管理 ==========
    /** 轮次编号 */
    public int turnNumber = 0;

    // ========== LLM响应 ==========
    /** LLM原始回复 */
    public String llmResponse = "";

    // ========== 输出审查 ==========
    /** 是否被OutputGuard拦截 */
    public boolean outputBlocked = false;
    /** 脱敏后的回复 */
    public String finalResponse = "";

    // ========== 追踪 ==========
    /** 本轮耗时(ms) */
    public long latencyMs = 0;
    /** 请求时间戳 */
    public long requestTimestamp = 0;

    // ========== 流转控制 ==========
    /** 是否应该终止管道（某一步拦截后，后面的步骤不需要执行） */
    public boolean shouldStop = false;

    /**
     * 构造一个请求上下文
     */
    public ChatContext(String userInput) {
        this.userInput = userInput;
        this.requestTimestamp = System.currentTimeMillis();
    }

    /**
     * 打印当前上下文状态（调试用）
     */
    public void printStatus() {
        System.out.println("┌─ ChatContext 状态 ─────────────────────");
        System.out.println("│ 用户输入: " + truncate(userInput, 40));
        System.out.println("│ InputGuard: " + (inputBlocked ? "🚫 拦截(" + inputBlockReason + ")" : "✅ 通过"));
        System.out.println("│ 缓存命中: " + (cacheHit ? "💚 是" : "🔴 否"));
        System.out.println("│ 选中模型: " + (selectedModel.isEmpty() ? "未选择" : selectedModel));
        System.out.println("│ 轮次: #" + turnNumber);
        System.out.println("│ LLM回复: " + truncate(llmResponse, 40));
        System.out.println("│ OutputGuard: " + (outputBlocked ? "🚫 拦截" : "✅ 通过"));
        System.out.println("│ 最终回复: " + truncate(finalResponse, 40));
        System.out.println("│ 耗时: " + latencyMs + "ms");
        System.out.println("│ 管道状态: " + (shouldStop ? "⏹️ 已终止" : "▶️ 继续流转"));
        System.out.println("└─────────────────────────────────────────");
    }

    private String truncate(String s, int max) {
        if (s == null) return "[null]";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
