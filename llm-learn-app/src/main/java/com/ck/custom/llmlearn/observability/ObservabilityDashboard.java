package com.ck.custom.llmlearn.observability;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可观测性Dashboard —— 控制台版
 *
 * 【Week7 Day6 Step2】
 *
 * 整合ProductionTokenTracker的数据，输出一个美观的控制台Dashboard。
 *
 * 对比OpenClaw的session_status：
 * - OpenClaw: /status 命令 → 一张状态卡（model/usage/time/cost/tasks）
 * - 本Dashboard: 调用render() → 控制台输出完整面板
 *
 * Dashboard分5个区域：
 * 1. 概览区 → 总调用/总Token/总成本/总耗时
 * 2. 模型对比区 → 多模型横向对比（调用数/Token/成本/延迟）
 * 3. 延迟分布区 → P50/P95/P99 + 快/正常/慢/很慢分级
 * 4. 成本分析区 → 每模型成本占比 + 单次调用平均成本
 * 5. 趋势区 → 最近20次调用的Token+延迟趋势图（ASCII柱状图）
 */
@Slf4j
public class ObservabilityDashboard {

    private final ProductionTokenTracker tokenTracker;

    public ObservabilityDashboard(ProductionTokenTracker tokenTracker) {
        this.tokenTracker = tokenTracker;
    }

    // ================================================================
    // 主渲染入口
    // ================================================================

    /**
     * 渲染完整Dashboard到控制台
     *
     * 使用方法：
     *   tracker 跑完一堆LLM调用后 → dashboard.render() → 打印面板
     *
     * 对比OpenClaw：
     *   OpenClaw每次heartbeat自动更新session_status
     *   我们需要手动调render()，但可以封装成定时任务
     */
    public void render() {
        String dashboard = buildDashboard();
        // 直接打印到控制台（不用log，避免日志格式干扰排版）
        System.out.println(dashboard);
    }

    /**
     * 构建Dashboard字符串（不直接打印，方便测试）
     */
    public String buildDashboard() {
        StringBuilder sb = new StringBuilder();

        renderHeader(sb);
        renderOverview(sb);
        renderModelComparison(sb);
        renderLatencyDistribution(sb);
        renderCostAnalysis(sb);
        renderTrendChart(sb);
        renderFooter(sb);

        return sb.toString();
    }

    // ================================================================
    // 区域1: 头部
    // ================================================================

    private void renderHeader(StringBuilder sb) {
        sb.append("\n");
        sb.append("┌").append("─".repeat(72)).append("┐\n");
        sb.append("│").append(center("📊 LLM Observability Dashboard", 72)).append("│\n");
        sb.append("│").append(center("Production Token & Cost & Latency Monitor", 72)).append("│\n");
        sb.append("└").append("─".repeat(72)).append("┘\n");
    }

    // ================================================================
    // 区域2: 概览
    // ================================================================

    private void renderOverview(StringBuilder sb) {
        sb.append("\n");
        sb.append("📌 概览\n");
        sb.append("─".repeat(72)).append("\n");
        sb.append(String.format("  LLM调用总数: %d次\n", tokenTracker.getGlobalCallCount()));
        sb.append(String.format("  输入Token:   %,d\n", tokenTracker.getGlobalInputTokens()));
        sb.append(String.format("  输出Token:   %,d\n", tokenTracker.getGlobalOutputTokens()));
        sb.append(String.format("  总Token:     %,d\n", tokenTracker.getGlobalTotalTokens()));
        sb.append(String.format("  总成本:      %s\n", formatCost(tokenTracker.getGlobalCostMicroYuan())));

        // 计算平均值
        int callCount = tokenTracker.getGlobalCallCount();
        if (callCount > 0) {
            long avgInput = tokenTracker.getGlobalInputTokens() / callCount;
            long avgOutput = tokenTracker.getGlobalOutputTokens() / callCount;
            long avgTotal = tokenTracker.getGlobalTotalTokens() / callCount;
            long avgCostMicro = tokenTracker.getGlobalCostMicroYuan() / callCount;
            sb.append(String.format("  ── 平均每次 ──\n"));
            sb.append(String.format("  平均输入:    %d Token\n", avgInput));
            sb.append(String.format("  平均输出:    %d Token\n", avgOutput));
            sb.append(String.format("  平均总Token: %d Token\n", avgTotal));
            sb.append(String.format("  平均成本:    %s/次\n", formatCost(avgCostMicro)));
        }
    }

    // ================================================================
    // 区域3: 模型对比
    // ================================================================

    private void renderModelComparison(StringBuilder sb) {
        Map<String, ProductionTokenTracker.ModelStats> stats = tokenTracker.getModelStats();

        if (stats.isEmpty()) {
            sb.append("\n");
            sb.append("🏷️ 模型对比\n");
            sb.append("─".repeat(72)).append("\n");
            sb.append("  （暂无数据）\n");
            return;
        }

        sb.append("\n");
        sb.append("🏷️ 模型对比\n");
        sb.append("─".repeat(72)).append("\n");

        // 表头
        sb.append(String.format("  %-22s %6s %10s %10s %10s %10s %8s\n",
                "模型", "调用数", "输入Tok", "输出Tok", "总Tok", "成本", "P95延迟"));
        sb.append("  ").append("-".repeat(68)).append("\n");

        // 找出总Token最多的模型（用于标注"最贵"）
        String mostExpensiveModel = null;
        long maxCost = 0;
        for (var entry : stats.entrySet()) {
            if (entry.getValue().getCostMicroYuan().get() > maxCost) {
                maxCost = entry.getValue().getCostMicroYuan().get();
                mostExpensiveModel = entry.getKey();
            }
        }

        for (var entry : stats.entrySet()) {
            ProductionTokenTracker.ModelStats s = entry.getValue();
            // 计算该模型的P95延迟
            long p95 = calculateP95(s.getLatencyHistory());
            String flag = entry.getKey().equals(mostExpensiveModel) ? " ⚡最贵" : "";
            sb.append(String.format("  %-22s %6d %10d %10d %10d %10s %7dms%s\n",
                    truncate(s.getModelName(), 22),
                    s.getCallCount().get(),
                    s.getInputTokens().get(),
                    s.getOutputTokens().get(),
                    s.getTotalTokens().get(),
                    formatCost(s.getCostMicroYuan().get()),
                    p95,
                    flag));
        }
    }

    // ================================================================
    // 区域4: 延迟分布
    // ================================================================

    private void renderLatencyDistribution(StringBuilder sb) {
        List<Long> allLatencies = getAllLatencies();

        sb.append("\n");
        sb.append("⏱️ 延迟分布\n");
        sb.append("─".repeat(72)).append("\n");

        if (allLatencies.isEmpty()) {
            sb.append("  （暂无延迟数据）\n");
            return;
        }

        // 分位数统计
        sb.append(String.format("  P50 (中位数):  %dms\n", percentile(allLatencies, 50)));
        sb.append(String.format("  P95:           %dms\n", percentile(allLatencies, 95)));
        sb.append(String.format("  P99:           %dms\n", percentile(allLatencies, 99)));
        sb.append(String.format("  最快:          %dms\n",
                allLatencies.stream().mapToLong(x -> x).min().orElse(0)));
        sb.append(String.format("  最慢:          %dms\n",
                allLatencies.stream().mapToLong(x -> x).max().orElse(0)));
        sb.append(String.format("  平均:          %.0fms\n",
                allLatencies.stream().mapToLong(x -> x).average().orElse(0)));

        // 速度分级（对比OpenClaw的healthcheck机制）
        // OpenClaw用heartbeat检测Agent是否健康，延迟异常=不健康
        sb.append("\n  速度分级:\n");

        int fast = 0, normal = 0, slow = 0, verySlow = 0;
        for (long latency : allLatencies) {
            if (latency < 2000) fast++;
            else if (latency < 5000) normal++;
            else if (latency < 10000) slow++;
            else verySlow++;
        }

        int total = allLatencies.size();
        sb.append(renderSpeedBar("⚡快(<2s)", fast, total));
        sb.append(renderSpeedBar("🟢正常(2-5s)", normal, total));
        sb.append(renderSpeedBar("🟡慢(5-10s)", slow, total));
        sb.append(renderSpeedBar("🔴很慢(>10s)", verySlow, total));
    }

    /**
     * 渲染速度分级的ASCII进度条
     *
     * 例:  ⚡快(<2s)   ████████████░░░░░░░░ 60.0% (12/20)
     */
    private String renderSpeedBar(String label, int count, int total) {
        double ratio = total > 0 ? (double) count / total : 0;
        int barWidth = 20;
        int filled = (int) (ratio * barWidth);
        String bar = "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, barWidth - filled));
        return String.format("    %-14s %s %5.1f%% (%d/%d)\n", label, bar, ratio * 100, count, total);
    }

    // ================================================================
    // 区域5: 成本分析
    // ================================================================

    private void renderCostAnalysis(StringBuilder sb) {
        Map<String, ProductionTokenTracker.ModelStats> stats = tokenTracker.getModelStats();
        long totalCost = tokenTracker.getGlobalCostMicroYuan();

        sb.append("\n");
        sb.append("💰 成本分析\n");
        sb.append("─".repeat(72)).append("\n");

        if (totalCost == 0) {
            sb.append("  （暂无成本数据）\n");
            return;
        }

        sb.append(String.format("  总成本: %s\n\n", formatCost(totalCost)));

        // 每个模型的成本占比
        sb.append("  成本占比:\n");

        // 按成本降序排列
        List<Map.Entry<String, ProductionTokenTracker.ModelStats>> sorted = new ArrayList<>(stats.entrySet());
        sorted.sort((a, b) -> Long.compare(
                b.getValue().getCostMicroYuan().get(),
                a.getValue().getCostMicroYuan().get()));

        for (var entry : sorted) {
            ProductionTokenTracker.ModelStats s = entry.getValue();
            long cost = s.getCostMicroYuan().get();
            double ratio = (double) cost / totalCost;
            int barWidth = 20;
            int filled = (int) (ratio * barWidth);
            String bar = "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, barWidth - filled));

            // 单次平均成本
            long avgPerCall = s.getCallCount().get() > 0 ? cost / s.getCallCount().get() : 0;

            sb.append(String.format("    %-22s %s %5.1f%% | %s | %d次, 每次%s\n",
                    truncate(s.getModelName(), 22),
                    bar,
                    ratio * 100,
                    formatCost(cost),
                    s.getCallCount().get(),
                    formatCost(avgPerCall)));
        }

        // 成本优化建议（对比Week7 Day4的ModelRouter策略）
        sb.append("\n  💡 优化建议:\n");
        if (!sorted.isEmpty()) {
            var topModel = sorted.get(0);
            var cheapModel = sorted.get(sorted.size() - 1);
            if (topModel.getValue().getCostMicroYuan().get() > cheapModel.getValue().getCostMicroYuan().get() * 2) {
                sb.append(String.format("    → %s成本是%s的%.1f倍，考虑用小模型处理简单任务\n",
                        truncate(topModel.getKey(), 15),
                        truncate(cheapModel.getKey(), 15),
                        (double) topModel.getValue().getCostMicroYuan().get()
                                / Math.max(1, cheapModel.getValue().getCostMicroYuan().get())));
            }
            // 检查是否有模型P95延迟过高
            for (var entry : sorted) {
                long p95 = calculateP95(entry.getValue().getLatencyHistory());
                if (p95 > 10000) {
                    sb.append(String.format("    → %s的P95延迟%dms过高，考虑切换模型或增加超时处理\n",
                            truncate(entry.getKey(), 15), p95));
                }
            }
        }
    }

    // ================================================================
    // 区域6: 趋势图（ASCII柱状图）
    // ================================================================

    /**
     * 渲染最近20次调用的Token+延迟趋势
     *
     * ASCII柱状图设计：
     * - 上半部分：Token数（█填充，按最大值归一化到30字符高度）
     * - 下半部分：延迟ms（▓填充，按最大值归一化到20字符高度）
     *
     * 对比OpenClaw的session transcript：
     * OpenClaw保留完整对话历史，但不会画图
     * 我们画趋势图是为了快速发现"Token突然飙升"或"延迟突然变慢"
     */
    private void renderTrendChart(StringBuilder sb) {
        List<ProductionTokenTracker.TokenCallRecord> records = tokenTracker.getCallRecords();

        sb.append("\n");
        sb.append("📈 最近调用趋势（Token + 延迟）\n");
        sb.append("─".repeat(72)).append("\n");

        if (records.isEmpty()) {
            sb.append("  （暂无调用记录）\n");
            return;
        }

        // 取最近20条
        int startIdx = Math.max(0, records.size() - 20);
        List<ProductionTokenTracker.TokenCallRecord> recent =
                new ArrayList<>(records.subList(startIdx, records.size()));

        // 找最大值用于归一化
        int maxTokens = recent.stream().mapToInt(ProductionTokenTracker.TokenCallRecord::totalTokens).max().orElse(1);
        long maxLatency = recent.stream().mapToLong(ProductionTokenTracker.TokenCallRecord::latencyMs).max().orElse(1);

        int chartHeight = 15;
        int barWidth = 3;  // 每条柱子的宽度

        // ---- Token柱状图（上半部分）----
        sb.append("  Token:\n");
        for (int row = chartHeight; row >= 1; row--) {
            sb.append("  ");
            int threshold = (int) ((double) row / chartHeight * maxTokens);
            for (ProductionTokenTracker.TokenCallRecord r : recent) {
                if (r.totalTokens() >= threshold) {
                    sb.append("█".repeat(barWidth));
                } else {
                    sb.append(" ".repeat(barWidth));
                }
                sb.append(" ");
            }
            // 在最上面一行标注最大值
            if (row == chartHeight) {
                sb.append(String.format(" ← max: %d", maxTokens));
            }
            sb.append("\n");
        }

        // X轴
        sb.append("  ");
        for (int i = 0; i < recent.size(); i++) {
            sb.append("-".repeat(barWidth)).append(" ");
        }
        sb.append("\n");

        // 调用序号
        sb.append("  ");
        for (ProductionTokenTracker.TokenCallRecord r : recent) {
            sb.append(String.format("%-" + barWidth + "d ", r.callNumber()));
        }
        sb.append("\n");

        // ---- 延迟柱状图（下半部分）----
        sb.append("\n  延迟(ms):\n");
        int latencyChartHeight = 10;
        for (int row = latencyChartHeight; row >= 1; row--) {
            sb.append("  ");
            long threshold = (long) ((double) row / latencyChartHeight * maxLatency);
            for (ProductionTokenTracker.TokenCallRecord r : recent) {
                if (r.latencyMs() >= threshold) {
                    sb.append("▓".repeat(barWidth));
                } else {
                    sb.append(" ".repeat(barWidth));
                }
                sb.append(" ");
            }
            if (row == latencyChartHeight) {
                sb.append(String.format(" ← max: %dms", maxLatency));
            }
            sb.append("\n");
        }

        // X轴
        sb.append("  ");
        for (int i = 0; i < recent.size(); i++) {
            sb.append("-".repeat(barWidth)).append(" ");
        }
        sb.append("\n");

        // 调用序号
        sb.append("  ");
        for (ProductionTokenTracker.TokenCallRecord r : recent) {
            sb.append(String.format("%-" + barWidth + "d ", r.callNumber()));
        }
        sb.append("\n");

        // 调用明细
        sb.append("\n  明细:\n");
        for (ProductionTokenTracker.TokenCallRecord r : recent) {
            sb.append(String.format("    #%d [%s] %s | in:%d out:%d total:%d | %dms | %s\n",
                    r.callNumber(),
                    r.timestamp().toString().substring(11, 19),
                    truncate(r.modelName(), 18),
                    r.inputTokens(),
                    r.outputTokens(),
                    r.totalTokens(),
                    r.latencyMs(),
                    formatCost(r.costMicroYuan())));
        }
    }

    // ================================================================
    // 底部
    // ================================================================

    private void renderFooter(StringBuilder sb) {
        sb.append("\n");
        sb.append("─".repeat(72)).append("\n");
        sb.append("  生成时间: ").append(java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");

        // 对比OpenClaw
        sb.append("\n");
        sb.append("  🔗 对比OpenClaw:\n");
        sb.append("  ────────────────────────────────────────────────────\n");
        sb.append("  OpenClaw session_status: 自动记录model/usage/time/cost\n");
        sb.append("  本Dashboard: 手动调render()，可封装成定时任务\n");
        sb.append("  OpenClaw heartbeat: 每30min检测Agent健康状态\n");
        sb.append("  本Dashboard延迟分级: 快/正常/慢/很慢，同样检测健康\n");
        sb.append("  ────────────────────────────────────────────────────\n");
    }

    // ================================================================
    // 工具方法
    // ================================================================

    /**
     * 字符串居中
     */
    private String center(String s, int width) {
        if (s.length() >= width) return s;
        int padding = (width - s.length()) / 2;
        return " ".repeat(padding) + s + " ".repeat(width - s.length() - padding);
    }

    /**
     * 计算P95延迟
     */
    private long calculateP95(List<Long> latencies) {
        if (latencies == null || latencies.isEmpty()) return 0;
        return percentile(latencies, 95);
    }

    /**
     * 计算分位数
     */
    private long percentile(List<Long> values, int p) {
        if (values == null || values.isEmpty()) return 0;
        List<Long> copy = new ArrayList<>(values);
        copy.sort(Long::compareTo);
        int index = (int) Math.ceil((p / 100.0) * copy.size()) - 1;
        index = Math.max(0, Math.min(index, copy.size() - 1));
        return copy.get(index);
    }

    /**
     * 获取所有延迟数据
     */
    private List<Long> getAllLatencies() {
        List<Long> all = new ArrayList<>();
        for (var stats : tokenTracker.getModelStats().values()) {
            all.addAll(stats.getLatencyHistory());
        }
        return all;
    }

    /**
     * 微元转可读字符串
     */
    private String formatCost(long microYuan) {
        if (microYuan == 0) return "¥0";
        double yuan = microYuan / 1_000_000.0;
        if (yuan < 0.01) return String.format("¥%.4f", yuan);
        return String.format("¥%.2f", yuan);
    }

    /**
     * 字符串截断
     */
    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
