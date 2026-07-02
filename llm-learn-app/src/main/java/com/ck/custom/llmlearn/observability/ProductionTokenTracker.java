package com.ck.custom.llmlearn.observability;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 生产级Token追踪器 —— 升级版TokenAccumulator
 *
 * 【Week7 Day6 Step1】
 *
 * 对比Week6的TokenAccumulator（简单计数器）：
 * - 旧版：只有4个AtomicInteger，统计总量
 * - 新版：按模型分类 + 时间序列 + 成本计算 + P50/P95延迟
 *
 * 对比OpenClaw的session_status机制：
 * - OpenClaw每次调用LLM会记录model/usage/time/cost，session_status命令展示
 * - 本类做同样的事：记录每次LLM调用的model/token/latency/cost
 * - 区别：OpenClaw是Gateway级别自动拦截，我们是在ChatModelListener层手动拦截
 *
 * 核心数据结构：
 * 1. modelStats → 按模型名分组的统计数据（token数/调用次数/成本）
 * 2. callRecords → 时间序列记录（每次调用的详细信息）
 * 3. latencyStats → 延迟分位数统计（P50/P95/P99）
 */
@Slf4j
public class ProductionTokenTracker implements ChatModelListener {

    // ========== 价格表（¥/千Token） ==========
    // 对比OpenClaw配置：models.price配置
    // 生产环境应该从配置文件读，这里硬编码常用模型
    private static final Map<String, ModelPricing> PRICING_TABLE = new LinkedHashMap<>();

    static {
        // SiliconFlow 模型定价参考：https://siliconflow.cn/pricing
        PRICING_TABLE.put("Pro/zai-org/GLM-5.1", new ModelPricing(6.000, 24.000));  // 输入¥0.5/M, 输出¥2/M
        PRICING_TABLE.put("Qwen/Qwen3-8B", new ModelPricing(0.0002, 0.0006));        // 便宜模型
        PRICING_TABLE.put("default", new ModelPricing(0.001, 0.002));                // 默认价格
    }

    // ========== 按模型分类的统计数据 ==========
    // key = 模型名, value = 该模型的累计统计
    private final Map<String, ModelStats> modelStats = new ConcurrentHashMap<>();

    // ========== 每次LLM调用的时间序列记录 ==========
    // 对比OpenClaw的session transcript：每条消息都有model/usage/time
    private final List<TokenCallRecord> callRecords = new ArrayList<>();
    private final Object recordsLock = new Object();

    // ========== 全局统计 ==========
    private final AtomicInteger globalCallCount = new AtomicInteger(0);
    private final AtomicLong globalInputTokens = new AtomicLong(0);
    private final AtomicLong globalOutputTokens = new AtomicLong(0);
    private final AtomicLong globalTotalTokens = new AtomicLong(0);
    // 用微元(10^-6元)避免浮点精度问题
    // 生产系统管钱必须用整数，float累加1万次可能偏移0.01
    private final AtomicLong globalCostMicroYuan = new AtomicLong(0);

    // ========== 请求时间记录（用于计算延迟） ==========
    // key = Thread.currentThread().getId()
    // 因为ChatModelListener的onRequest和onResponse可能在同一线程上调用
    // LangChain4j默认同步调用，所以用线程ID关联请求和响应
    private final ConcurrentHashMap<Long, Long> requestStartTimes = new ConcurrentHashMap<>();

    // ========== Hook: 请求发出前 ==========

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        long threadId = Thread.currentThread().getId();
        long startMs = System.currentTimeMillis();
        requestStartTimes.put(threadId, startMs);

        int callNum = globalCallCount.incrementAndGet();
        String modelName = requestContext.chatRequest() != null
                ? requestContext.chatRequest().modelName()
                : "unknown";
        log.info("📊 [TOKEN-TRACK] 请求#{} | 模型:{} | 线程:{}", callNum, modelName, threadId);
    }

    // ========== Hook: 响应回来后 ==========

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        long threadId = Thread.currentThread().getId();
        long endMs = System.currentTimeMillis();
        Long startMs = requestStartTimes.remove(threadId);
        long latencyMs = startMs != null ? (endMs - startMs) : -1;

        // 防御性检查：agentic模块的bug可能导致chatResponse为null
        if (responseContext.chatResponse() == null
                || responseContext.chatResponse().tokenUsage() == null) {
            log.warn("📊 [TOKEN-TRACK] 响应为null，无法统计Token");
            return;
        }

        var tokenUsage = responseContext.chatResponse().tokenUsage();
        int inputTokens = tokenUsage.inputTokenCount() != null ? tokenUsage.inputTokenCount() : 0;
        int outputTokens = tokenUsage.outputTokenCount() != null ? tokenUsage.outputTokenCount() : 0;
        int totalTokens = tokenUsage.totalTokenCount() != null
                ? tokenUsage.totalTokenCount()
                : (inputTokens + outputTokens);

        // 获取模型名（响应里的模型名更准确，因为请求可能被路由到不同模型）
        String modelName = responseContext.chatRequest() != null
                ? responseContext.chatRequest().modelName()
                : "unknown";
        if (modelName == null) modelName = "unknown";

        // 查价格表，没有匹配的用default
        ModelPricing pricing = PRICING_TABLE.getOrDefault(modelName, PRICING_TABLE.get("default"));
        // 成本计算（微元）：
        // inputTokens / 1000 * inputPricePer1K * 1_000_000
        // 例：1000输入Token * 0.0005¥/千Token * 1_000_000 = 500微元 = ¥0.0005
        long costMicroYuan = (long) (inputTokens / 1000.0 * pricing.inputPricePer1K * 1_000_000)
                + (long) (outputTokens / 1000.0 * pricing.outputPricePer1K * 1_000_000);

        // 更新全局统计
        globalInputTokens.addAndGet(inputTokens);
        globalOutputTokens.addAndGet(outputTokens);
        globalTotalTokens.addAndGet(totalTokens);
        globalCostMicroYuan.addAndGet(costMicroYuan);

        // 更新按模型分类统计
        String finalModelName = modelName;
        ModelStats stats = modelStats.computeIfAbsent(modelName, k -> new ModelStats(finalModelName));
        stats.addCall(inputTokens, outputTokens, totalTokens, costMicroYuan, latencyMs);

        // 记录时间序列（用于后续分析和Dashboard展示）
        TokenCallRecord record = new TokenCallRecord(
                globalCallCount.get(), modelName, inputTokens, outputTokens,
                totalTokens, costMicroYuan, latencyMs, Instant.now()
        );
        synchronized (recordsLock) {
            callRecords.add(record);
        }

        log.info("📊 [TOKEN-TRACK] 响应#{} | 模型:{} | 输入:{} | 输出:{} | 总:{} | 延迟:{}ms | 成本:{}",
                globalCallCount.get(), modelName, inputTokens, outputTokens, totalTokens,
                latencyMs, formatCost(costMicroYuan));
    }

    // ========== Hook: 调用出错 ==========

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        long threadId = Thread.currentThread().getId();
        requestStartTimes.remove(threadId);
        log.error("📊 [TOKEN-TRACK] 错误: {}", errorContext.error().getMessage());
    }

    // ================================================================
    // 报告生成
    // ================================================================

    /**
     * 生成完整的Token追踪报告
     *
     * 对比OpenClaw的session_status输出格式：
     * ┌─────────────────────────────────┐
     * │ Model: byai/glm-5.2            │
     * │ Usage: 15.2k tokens            │
     * │ Time: 2h 30m                   │
     * │ Cost: ¥0.03                    │
     * └─────────────────────────────────┘
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=" .repeat(70)).append("\n");
        sb.append("📊 Production Token Tracker — 完整报告\n");
        sb.append("=" .repeat(70)).append("\n\n");

        // ---- 全局概览 ----
        sb.append("🌐 全局概览:\n");
        sb.append("-".repeat(70)).append("\n");
        sb.append(String.format("  LLM调用总数: %d次\n", globalCallCount.get()));
        sb.append(String.format("  输入Token: %,d\n", globalInputTokens.get()));
        sb.append(String.format("  输出Token: %,d\n", globalOutputTokens.get()));
        sb.append(String.format("  总Token: %,d\n", globalTotalTokens.get()));
        sb.append(String.format("  总成本: %s\n", formatCost(globalCostMicroYuan.get())));
        sb.append("\n");

        // ---- 按模型分类统计 ----
        sb.append("🏷️ 按模型分类:\n");
        sb.append("-".repeat(70)).append("\n");
        sb.append(String.format("  %-25s %-8s %-10s %-10s %-10s %-12s\n",
                "模型", "调用数", "输入Tok", "输出Tok", "总Tok", "成本(¥)"));
        for (ModelStats stats : modelStats.values()) {
            sb.append(String.format("  %-25s %-8d %-10d %-10d %-10d %-12s\n",
                    truncate(stats.modelName, 25),
                    stats.callCount.get(),
                    stats.inputTokens.get(),
                    stats.outputTokens.get(),
                    stats.totalTokens.get(),
                    formatCost(stats.costMicroYuan.get())));
        }
        sb.append("\n");

        // ---- 延迟统计（全局P50/P95/P99） ----
        List<Long> allLatencies = getAllLatencies();
        if (!allLatencies.isEmpty()) {
            sb.append("⏱️ 延迟统计:\n");
            sb.append("-".repeat(70)).append("\n");
            sb.append(String.format("  P50: %dms\n", percentile(allLatencies, 50)));
            sb.append(String.format("  P95: %dms\n", percentile(allLatencies, 95)));
            sb.append(String.format("  P99: %dms\n", percentile(allLatencies, 99)));
            sb.append(String.format("  最快: %dms\n", allLatencies.stream().mapToLong(x -> x).min().orElse(0)));
            sb.append(String.format("  最慢: %dms\n", allLatencies.stream().mapToLong(x -> x).max().orElse(0)));
            sb.append(String.format("  平均: %.0fms\n", allLatencies.stream().mapToLong(x -> x).average().orElse(0)));
            sb.append("\n");
        }

        // ---- 最近10次调用明细 ----
        sb.append("📋 最近10次调用:\n");
        sb.append("-".repeat(70)).append("\n");
        synchronized (recordsLock) {
            int start = Math.max(0, callRecords.size() - 10);
            for (int i = start; i < callRecords.size(); i++) {
                TokenCallRecord r = callRecords.get(i);
                sb.append(String.format("  #%d [%s] %s | in:%d out:%d total:%d | %dms | %s\n",
                        r.callNumber,
                        r.timestamp.toString().substring(11, 19),  // 只取时间部分 HH:MM:SS
                        truncate(r.modelName, 20),
                        r.inputTokens, r.outputTokens,
                        r.totalTokens, r.latencyMs,
                        formatCost(r.costMicroYuan)));
            }
        }
        sb.append("\n");

        // ---- 对比OpenClaw ----
        sb.append("🔗 对比OpenClaw session_status:\n");
        sb.append("-".repeat(70)).append("\n");
        sb.append("  OpenClaw: Gateway层自动拦截，session_status展示model/usage/time/cost\n");
        sb.append("  本Tracker: ChatModelListener层拦截，手动注册到ChatModel\n");
        sb.append("  相同点: 都记录model/token/latency/cost\n");
        sb.append("  不同点: OpenClaw是运行时自动；我们需要手动注册Listener\n");
        sb.append("=" .repeat(70)).append("\n");

        return sb.toString();
    }

    /**
     * 生成一行摘要（用于Dashboard拼接）
     */
    public String summary() {
        return String.format("调用%d次 | 总Token:%,d | 成本:%s | P50:%dms | P95:%dms",
                globalCallCount.get(),
                globalTotalTokens.get(),
                formatCost(globalCostMicroYuan.get()),
                getGlobalP50(),
                getGlobalP95());
    }

    // ================================================================
    // P50/P95 获取方法
    // ================================================================

    public int getGlobalP50() {
        List<Long> latencies = getAllLatencies();
        return latencies.isEmpty() ? 0 : (int) percentile(latencies, 50);
    }

    public int getGlobalP95() {
        List<Long> latencies = getAllLatencies();
        return latencies.isEmpty() ? 0 : (int) percentile(latencies, 95);
    }

    // ================================================================
    // 工具方法
    // ================================================================

    private List<Long> getAllLatencies() {
        List<Long> all = new ArrayList<>();
        for (ModelStats stats : modelStats.values()) {
            all.addAll(stats.latencyHistory);
        }
        return all;
    }

    /**
     * 计算分位数（P50/P95/P99）
     *
     * 算法：排序后取第 ceil(p/100 * n) 个元素
     *
     * 对比OpenClaw的延迟统计：
     * OpenClaw Gateway内部也有P50/P95统计，用于判断模型是否变慢
     * 如果P95突然飙升，说明模型端或网络出了问题
     */
    private long percentile(List<Long> values, int p) {
        if (values.isEmpty()) return 0;
        // 复制一份再排序，不修改原列表
        List<Long> copy = new ArrayList<>(values);
        copy.sort(Long::compareTo);
        int index = (int) Math.ceil((p / 100.0) * copy.size()) - 1;
        index = Math.max(0, Math.min(index, copy.size() - 1));
        return copy.get(index);
    }

    /**
     * 微元转可读字符串
     *
     * 1微元 = 0.000001元
     * 1000微元 = 0.001元
     * 1000000微元 = 1元
     *
     * 小额成本保留4位小数，大额保留2位
     */
    private String formatCost(long microYuan) {
        if (microYuan == 0) return "¥0";
        double yuan = microYuan / 1_000_000.0;
        if (yuan < 0.01) {
            return String.format("¥%.4f", yuan);
        }
        return String.format("¥%.2f", yuan);
    }

    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ================================================================
    // Getter
    // ================================================================

    public Map<String, ModelStats> getModelStats() { return modelStats; }

    public List<TokenCallRecord> getCallRecords() {
        synchronized (recordsLock) {
            return new ArrayList<>(callRecords);
        }
    }

    public long getGlobalInputTokens() { return globalInputTokens.get(); }
    public long getGlobalOutputTokens() { return globalOutputTokens.get(); }
    public long getGlobalTotalTokens() { return globalTotalTokens.get(); }
    public long getGlobalCostMicroYuan() { return globalCostMicroYuan.get(); }
    public int getGlobalCallCount() { return globalCallCount.get(); }

    /**
     * 重置所有统计（用于每次测试清空）
     */
    public void reset() {
        modelStats.clear();
        synchronized (recordsLock) {
            callRecords.clear();
        }
        globalCallCount.set(0);
        globalInputTokens.set(0);
        globalOutputTokens.set(0);
        globalTotalTokens.set(0);
        globalCostMicroYuan.set(0);
        requestStartTimes.clear();
    }

    // ================================================================
    // 内部数据模型
    // ================================================================

    /**
     * 模型定价（¥/千Token）
     *
     * @param inputPricePer1K  每千输入Token的价格（元）
     * @param outputPricePer1K 每千输出Token的价格（元）
     */
    public record ModelPricing(double inputPricePer1K, double outputPricePer1K) {}

    /**
     * 按模型分类的统计数据
     *
     * 对比Week6的TokenAccumulator：
     * - 旧版：全局4个AtomicInteger，不分模型
     * - 新版：每个模型独立统计，支持多模型对比分析
     *
     * 线程安全说明：
     * - AtomicXxx 保证单个操作的原子性
     * - latencyHistory 是ArrayList，用synchronized保护
     * - 在LangChain4j同步调用模式下，实际上不会并发，但防御性编程无害
     */
    public static class ModelStats {
        private final String modelName;
        private final AtomicInteger callCount = new AtomicInteger(0);
        private final AtomicLong inputTokens = new AtomicLong(0);
        private final AtomicLong outputTokens = new AtomicLong(0);
        private final AtomicLong totalTokens = new AtomicLong(0);
        private final AtomicLong costMicroYuan = new AtomicLong(0);
        private final List<Long> latencyHistory = new ArrayList<>();

        public ModelStats(String modelName) {
            this.modelName = modelName;
        }

        // ========== Getter 方法（供 ObservabilityDashboard 访问） ==========

        public String getModelName() { return modelName; }
        public AtomicInteger getCallCount() { return callCount; }
        public AtomicLong getInputTokens() { return inputTokens; }
        public AtomicLong getOutputTokens() { return outputTokens; }
        public AtomicLong getTotalTokens() { return totalTokens; }
        public AtomicLong getCostMicroYuan() { return costMicroYuan; }
        public List<Long> getLatencyHistory() { return latencyHistory; }

        /**
         * 记录一次LLM调用的数据
         */
        public void addCall(int input, int output, int total, long cost, long latency) {
            callCount.incrementAndGet();
            inputTokens.addAndGet(input);
            outputTokens.addAndGet(output);
            totalTokens.addAndGet(total);
            costMicroYuan.addAndGet(cost);
            if (latency >= 0) {
                synchronized (latencyHistory) {
                    latencyHistory.add(latency);
                }
            }
        }
    }

    /**
     * 单次LLM调用记录（时间序列）
     *
     * 对比OpenClaw的session transcript每条消息记录：
     * OpenClaw每条消息都记录model/usage/time/cost，用于后续审计
     * 本类做同样的事，保留每次调用的完整快照
     *
     * @param callNumber     调用序号（全局递增）
     * @param modelName      模型名
     * @param inputTokens    输入Token数
     * @param outputTokens   输出Token数
     * @param totalTokens    总Token数
     * @param costMicroYuan  成本（微元）
     * @param latencyMs      延迟（毫秒）
     * @param timestamp      时间戳
     */
    public record TokenCallRecord(
            int callNumber,
            String modelName,
            int inputTokens,
            int outputTokens,
            int totalTokens,
            long costMicroYuan,
            long latencyMs,
            Instant timestamp
    ) {}
}
