package com.ck.custom.llmlearn.observability;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 质量下降预警监控器
 *
 * 【Week7 Day6 Step3】
 *
 * 核心职责：监控LLM输出的"健康状态"，发现异常及时告警。
 *
 * 三大监控维度：
 * 1. 输出长度异常 → 突然变短（模型偷懒/被截断）或突然变长（幻觉/重复生成）
 * 2. 错误率飙升 → 超时、API异常、模型不可用
 * 3. 延迟退化 → 同样的请求越来越慢（模型端过载或上下文膨胀）
 *
 * 对比OpenClaw的heartbeat机制：
 * - OpenClaw: 每30min发一次heartbeat，检测Agent是否响应、是否卡死
 *   如果heartbeat超时没响应 → 判定Agent不健康 → 重启/告警
 * - 本监控器: 每次LLM调用后检查输出质量，发现异常 → 触发Alert
 *   区别：OpenClaw检测"活着没"，我们检测"活得好不好"
 *
 * 设计模式：
 * - 滑动窗口（Ring Buffer）→ 只看最近N次调用，快速发现趋势变化
 * - 规则引擎 → 每条规则独立判断，可插拔
 * - 观察者模式 → 异常时通知所有注册的AlertHandler
 */
@Slf4j
public class QualityAlertMonitor {

    // ========== 滑动窗口大小 ==========
    // 对比OpenClaw的contextPruning：它保留最近30min的上下文
    // 我们保留最近20次调用的数据用于趋势分析
    private static final int WINDOW_SIZE = 20;

    // ========== 异常检测阈值 ==========
    // 输出长度异常：与滑动窗口平均值偏差超过这个倍数
    private static final double LENGTH_ANOMALY_RATIO = 3.0;  // 平均值的3倍
    // 最小输出长度（低于此值判定为"过短"）
    private static final int MIN_OUTPUT_LENGTH = 10;
    // 最大输出长度（超过此值判定为"过长"）
    private static final int MAX_OUTPUT_LENGTH = 10000;

    // 错误率阈值：滑动窗口内错误比例超过此值触发告警
    private static final double ERROR_RATE_THRESHOLD = 0.3;  // 30%

    // 延迟退化阈值：最近P95比历史P95高出此倍数
    private static final double LATENCY_DEGRADATION_RATIO = 2.0;  // 翻倍

    // ========== 滑动窗口数据（Ring Buffer） ==========
    // 用ArrayDeque实现，比LinkedList更省内存
    private final Deque<CallSnapshot> slidingWindow = new ArrayDeque<>(WINDOW_SIZE);

    // ========== 历史基线（用于延迟退化对比） ==========
    // 前 WINDOW_SIZE 次调用的P95作为基线，后续与之对比
    private long baselineP95 = -1;
    private int callCountSinceBaseline = 0;

    // ========== 告警处理器列表（观察者模式） ==========
    private final List<AlertHandler> alertHandlers = new ArrayList<>();

    // ========== 统计计数 ==========
    private int totalAlerts = 0;
    private int lengthAlerts = 0;
    private int errorAlerts = 0;
    private int latencyAlerts = 0;

    // ================================================================
    // 公开接口
    // ================================================================

    /**
     * 注册告警处理器（观察者模式）
     *
     * 使用方式：
     *   monitor.registerHandler(alert -> log.warn("告警: " + alert));
     *   monitor.registerHandler(alert -> sendToSlack(alert));
     */
    public void registerHandler(AlertHandler handler) {
        alertHandlers.add(handler);
    }

    /**
     * 记录一次LLM调用并检查质量
     *
     * 每次LLM调用完成后调用此方法，传入调用结果
     * 方法内部会做三件事：
     * 1. 把快照加入滑动窗口
     * 2. 运行三条检测规则
     * 3. 有异常就触发告警
     *
     * @param outputLength  LLM输出的字符长度
     * @param latencyMs     本次调用延迟（毫秒）
     * @param isError       是否出错（超时/异常/空响应）
     * @param modelName     模型名（用于告警信息）
     */
    public void record(int outputLength, long latencyMs, boolean isError, String modelName) {
        CallSnapshot snapshot = new CallSnapshot(outputLength, latencyMs, isError, modelName, System.currentTimeMillis());
        addToWindow(snapshot);
        callCountSinceBaseline++;

        // 三条检测规则（独立运行，互不影响）
        checkOutputLength(snapshot, modelName);
        checkErrorRate(modelName);
        checkLatencyDegradation(snapshot, modelName);

        // 每 WINDOW_SIZE 次更新一次基线
        if (callCountSinceBaseline >= WINDOW_SIZE) {
            updateBaseline();
        }
    }

    // ================================================================
    // 规则1：输出长度异常检测
    // ================================================================

    /**
     * 检测输出长度异常
     *
     * 两种异常：
     * 1. 过短 → 模型偷懒、被截断、或返回了错误信息
     * 2. 过长 → 幻觉、重复生成、或上下文膨胀导致输出失控
     *
     * 算法：
     * - 计算滑动窗口内所有调用的平均输出长度
     * - 如果当前输出偏离平均值超过 LENGTH_ANOMALY_RATIO 倍 → 告警
     * - 同时检查绝对阈值（低于MIN或高于MAX）
     *
     * 对比OpenClaw：
     * OpenClaw的compaction机制会检测对话长度，过长时触发压缩
     * 但它不检测"输出突然变短"——这是我们的增强
     */
    private void checkOutputLength(CallSnapshot snapshot, String modelName) {
        int outputLength = snapshot.outputLength;

        // 绝对阈值检测
        if (outputLength < MIN_OUTPUT_LENGTH) {
            triggerAlert(AlertLevel.WARNING, AlertType.OUTPUT_TOO_SHORT,
                    String.format("输出过短: %d字符 (阈值<%d) | 模型:%s | 可能被截断或返回错误",
                            outputLength, MIN_OUTPUT_LENGTH, modelName));
            return;
        }

        if (outputLength > MAX_OUTPUT_LENGTH) {
            triggerAlert(AlertLevel.CRITICAL, AlertType.OUTPUT_TOO_LONG,
                    String.format("输出过长: %d字符 (阈值>%d) | 模型:%s | 可能幻觉或重复生成",
                            outputLength, MAX_OUTPUT_LENGTH, modelName));
            return;
        }

        // 相对偏差检测（需要至少5个样本才有统计意义）
        if (slidingWindow.size() >= 5) {
            double avgLength = slidingWindow.stream()
                    .mapToInt(s -> s.outputLength)
                    .average()
                    .orElse(0);

            if (avgLength > 0) {
                double ratio = outputLength / avgLength;
                if (ratio > LENGTH_ANOMALY_RATIO) {
                    triggerAlert(AlertLevel.WARNING, AlertType.OUTPUT_TOO_LONG,
                            String.format("输出异常长: %d字符 (平均%.0f, 偏差%.1f倍) | 模型:%s",
                                    outputLength, avgLength, ratio, modelName));
                } else if (ratio < 1.0 / LENGTH_ANOMALY_RATIO && outputLength < avgLength * 0.3) {
                    triggerAlert(AlertLevel.WARNING, AlertType.OUTPUT_TOO_SHORT,
                            String.format("输出异常短: %d字符 (平均%.0f, 偏差%.1f%%) | 模型:%s",
                                    outputLength, avgLength, ratio * 100, modelName));
                }
            }
        }
    }

    // ================================================================
    // 规则2：错误率飙升检测
    // ================================================================

    /**
     * 检测错误率飙升
     *
     * 算法：
     * - 统计滑动窗口内的错误次数
     * - 如果错误率超过 ERROR_RATE_THRESHOLD → 告警
     *
     * 对比OpenClaw的heartbeat：
     * - OpenClaw: heartbeat超时 → Agent可能挂了 → 重启
     * - 本规则: 连续30%调用出错 → 模型/API有问题 → 告警
     * - 区别：OpenClaw检测"完全无响应"，我们检测"响应质量下降"
     *
     * 生产场景：
     * - API限流（429）→ 短暂错误率飙升 → 应该降级到备用模型
     * - 模型端故障（500）→ 持续错误 → 应该切换Provider
     */
    private void checkErrorRate(String modelName) {
        if (slidingWindow.size() < 5) return;  // 样本不足，不判断

        long errorCount = slidingWindow.stream().filter(s -> s.isError).count();
        double errorRate = (double) errorCount / slidingWindow.size();

        if (errorRate >= ERROR_RATE_THRESHOLD) {
            AlertLevel level = errorRate >= 0.5 ? AlertLevel.CRITICAL : AlertLevel.WARNING;
            triggerAlert(level, AlertType.ERROR_RATE_HIGH,
                    String.format("错误率飙升: %.1f%% (%d/%d) | 模型:%s | 阈值:%.0f%%",
                            errorRate * 100, errorCount, slidingWindow.size(),
                            modelName, ERROR_RATE_THRESHOLD * 100));
        }
    }

    // ================================================================
    // 规则3：延迟退化检测
    // ================================================================

    /**
     * 检测延迟退化（越来越慢）
     *
     * 算法：
     * - 用前 WINDOW_SIZE 次调用的P95作为基线
     * - 计算最近5次调用的P95
     * - 如果近期P95 > 基线P95 * LATENCY_DEGRADATION_RATIO → 告警
     *
     * 对比OpenClaw：
     * OpenClaw的heartbeat本身就有超时检测——如果Agent在heartbeat周期内
     * 没有响应，就判定为不健康。但它不检测"逐渐变慢"，只检测"完全卡死"。
     * 我们检测趋势退化，能在卡死之前发现问题。
     *
     * 生产场景：
     * - 上下文膨胀 → 每轮对话的Token越来越多 → 延迟逐渐上升
     * - 模型端过载 → 排队时间变长 → 延迟翻倍
     * - 网络退化 → 传输延迟增加
     */
    private void checkLatencyDegradation(CallSnapshot snapshot, String modelName) {
        if (baselineP95 < 0) return;  // 还没建立基线
        if (slidingWindow.size() < 10) return;  // 样本不足

        // 计算最近5次的P95
        List<Long> recentLatencies = new ArrayList<>();
        var iterator = slidingWindow.descendingIterator();
        int count = 0;
        while (iterator.hasNext() && count < 5) {
            recentLatencies.add(iterator.next().latencyMs);
            count++;
        }

        if (recentLatencies.size() < 5) return;

        long recentP95 = percentile(recentLatencies, 95);

        if (recentP95 > baselineP95 * LATENCY_DEGRADATION_RATIO) {
            triggerAlert(AlertLevel.WARNING, AlertType.LATENCY_DEGRADATION,
                    String.format("延迟退化: 近5次P95=%dms vs 基线P95=%dms (退化%.1f倍) | 模型:%s",
                            recentP95, baselineP95,
                            (double) recentP95 / baselineP95, modelName));
        }
    }

    // ================================================================
    // 基线更新
    // ================================================================

    /**
     * 更新基线P95
     *
     * 用前 WINDOW_SIZE 次调用的P95作为基线
     * 后续调用与之对比，如果延迟翻倍就告警
     *
     * 注意：基线只在第一次窗口满了之后建立一次
     * 后续不更新基线，否则退化会被"温水煮青蛙"掩盖
     */
    private void updateBaseline() {
        if (baselineP95 >= 0) return;  // 只建一次

        List<Long> allLatencies = new ArrayList<>();
        for (CallSnapshot s : slidingWindow) {
            allLatencies.add(s.latencyMs);
        }
        baselineP95 = percentile(allLatencies, 95);
        log.info("📊 [QUALITY-MONITOR] 基线P95建立: {}ms (基于{}次调用)", baselineP95, slidingWindow.size());

        // 重置计数器，后续不再更新基线
        callCountSinceBaseline = 0;
    }

    // ================================================================
    // 告警触发
    // ================================================================

    /**
     * 触发告警，通知所有注册的AlertHandler
     */
    private void triggerAlert(AlertLevel level, AlertType type, String message) {
        totalAlerts++;

        // 按类型计数
        switch (type) {
            case OUTPUT_TOO_SHORT, OUTPUT_TOO_LONG -> lengthAlerts++;
            case ERROR_RATE_HIGH -> errorAlerts++;
            case LATENCY_DEGRADATION -> latencyAlerts++;
        }

        Alert alert = new Alert(level, type, message, System.currentTimeMillis());

        // 日志输出
        switch (level) {
            case CRITICAL -> log.error("🚨 [QUALITY-ALERT][CRITICAL] {}", message);
            case WARNING -> log.warn("⚠️ [QUALITY-ALERT][WARNING] {}", message);
            case INFO -> log.info("ℹ️ [QUALITY-ALERT][INFO] {}", message);
        }

        // 通知所有处理器
        for (AlertHandler handler : alertHandlers) {
            try {
                handler.handle(alert);
            } catch (Exception e) {
                log.warn("告警处理器异常: {}", e.getMessage());
            }
        }
    }

    // ================================================================
    // 滑动窗口管理
    // ================================================================

    private void addToWindow(CallSnapshot snapshot) {
        slidingWindow.addLast(snapshot);
        if (slidingWindow.size() > WINDOW_SIZE) {
            slidingWindow.removeFirst();
        }
    }

    // ================================================================
    // 报告生成
    // ================================================================

    /**
     * 生成质量监控报告
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=" .repeat(70)).append("\n");
        sb.append("🩺 Quality Alert Monitor — 质量监控报告\n");
        sb.append("=" .repeat(70)).append("\n\n");

        // 窗口状态
        sb.append("📊 滑动窗口:\n");
        sb.append("-".repeat(70)).append("\n");
        sb.append(String.format("  窗口大小: %d/%d\n", slidingWindow.size(), WINDOW_SIZE));
        if (!slidingWindow.isEmpty()) {
            long errorCount = slidingWindow.stream().filter(s -> s.isError).count();
            double avgLatency = slidingWindow.stream().mapToLong(s -> s.latencyMs).average().orElse(0);
            double avgOutputLen = slidingWindow.stream().mapToInt(s -> s.outputLength).average().orElse(0);
            sb.append(String.format("  错误次数: %d (%.1f%%)\n", errorCount, (double) errorCount / slidingWindow.size() * 100));
            sb.append(String.format("  平均延迟: %.0fms\n", avgLatency));
            sb.append(String.format("  平均输出长度: %.0f字符\n", avgOutputLen));
        }
        if (baselineP95 >= 0) {
            sb.append(String.format("  基线P95: %dms\n", baselineP95));
        } else {
            sb.append("  基线P95: 未建立（等待更多数据）\n");
        }
        sb.append("\n");

        // 告警统计
        sb.append("🚨 告警统计:\n");
        sb.append("-".repeat(70)).append("\n");
        sb.append(String.format("  总告警次数: %d\n", totalAlerts));
        sb.append(String.format("    - 输出长度异常: %d\n", lengthAlerts));
        sb.append(String.format("    - 错误率飙升:   %d\n", errorAlerts));
        sb.append(String.format("    - 延迟退化:     %d\n", latencyAlerts));
        sb.append("\n");

        // 健康状态评估
        sb.append("🏥 健康状态:\n");
        sb.append("-".repeat(70)).append("\n");
        String healthStatus = assessHealth();
        sb.append(String.format("  状态: %s\n", healthStatus));
        sb.append("\n");

        // 对比OpenClaw
        sb.append("🔗 对比OpenClaw heartbeat:\n");
        sb.append("-".repeat(70)).append("\n");
        sb.append("  OpenClaw heartbeat: 每30min检测Agent是否响应 → 卡死则重启\n");
        sb.append("  本监控器: 每次调用后检测输出质量 → 异常则告警\n");
        sb.append("  OpenClaw检测\"活着没\"，我们检测\"活得好不好\"\n");
        sb.append("  两者互补：OpenClaw管可用性，本监控器管质量\n");
        sb.append("=" .repeat(70)).append("\n");

        return sb.toString();
    }

    /**
     * 评估当前健康状态
     *
     * 逻辑：
     * - 最近10次调用中有CRITICAL告警 → 🔴 不健康
     * - 最近10次调用中有WARNING告警 → 🟡 亚健康
     * - 无告警 → 🟢 健康
     */
    private String assessHealth() {
        if (slidingWindow.isEmpty()) return "🟢 健康（无数据）";
        if (totalAlerts == 0) return "🟢 健康";

        // 简化判断：看告警总数与窗口大小的比例
        double alertRatio = (double) totalAlerts / Math.max(1, slidingWindow.size());
        if (alertRatio > 0.3) return "🔴 不健康（告警率" + String.format("%.0f%%", alertRatio * 100) + "）";
        if (alertRatio > 0.1) return "🟡 亚健康（告警率" + String.format("%.0f%%", alertRatio * 100) + "）";
        return "🟢 健康（偶发告警）";
    }

    /**
     * 一行摘要
     */
    public String summary() {
        return String.format("告警%d次(长度%d/错误%d/延迟%d) | 健康:%s",
                totalAlerts, lengthAlerts, errorAlerts, latencyAlerts,
                slidingWindow.isEmpty() ? "无数据" : (totalAlerts == 0 ? "🟢" : "🟡"));
    }

    // ================================================================
    // 工具方法
    // ================================================================

    private long percentile(List<Long> values, int p) {
        if (values.isEmpty()) return 0;
        List<Long> copy = new ArrayList<>(values);
        copy.sort(Long::compareTo);
        int index = (int) Math.ceil((p / 100.0) * copy.size()) - 1;
        index = Math.max(0, Math.min(index, copy.size() - 1));
        return copy.get(index);
    }

    // ================================================================
    // 内部数据模型
    // ================================================================

    /**
     * 单次调用快照（滑动窗口元素）
     *
     * 只保留质量检测需要的最小信息，不保留完整输出（省内存）
     */
    private record CallSnapshot(
            int outputLength,     // 输出字符长度
            long latencyMs,       // 延迟毫秒
            boolean isError,      // 是否出错
            String modelName,     // 模型名
            long timestamp        // 时间戳
    ) {}

    /**
     * 告警级别
     */
    public enum AlertLevel {
        INFO,       // 信息（如基线建立）
        WARNING,    // 警告（需要关注但不紧急）
        CRITICAL    // 严重（必须立即处理）
    }

    /**
     * 告警类型
     */
    public enum AlertType {
        OUTPUT_TOO_SHORT,    // 输出过短
        OUTPUT_TOO_LONG,     // 输出过长
        ERROR_RATE_HIGH,     // 错误率飙升
        LATENCY_DEGRADATION  // 延迟退化
    }

    /**
     * 告警对象
     */
    public record Alert(
            AlertLevel level,
            AlertType type,
            String message,
            long timestamp
    ) {}

    /**
     * 告警处理器接口（观察者模式）
     *
     * 使用方式：
     *   monitor.registerHandler(alert -> {
     *       if (alert.level() == AlertLevel.CRITICAL) {
     *           sendPagerDuty(alert);
     *       }
     *   });
     */
    @FunctionalInterface
    public interface AlertHandler {
        void handle(Alert alert);
    }
}
