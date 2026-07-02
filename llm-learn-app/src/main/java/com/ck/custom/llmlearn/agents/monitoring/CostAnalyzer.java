package com.ck.custom.llmlearn.agents.monitoring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent成本分析器
 *
 * 核心思路：从TraceListener的traces里提取两个维度的成本数据
 * 1. Token消耗 → 每次LLM调用烧了多少Token？总计多少？
 * 2. 时间消耗 → 每个工具/Agent调用花了多久？时间分布如何？
 *
 * 为什么需要成本分析？
 * - Token是直接的钱：SiliconFlow GLM-5.1 约 ¥0.001/千Token
 * - 时间是间接的钱：慢=用户体验差=流失率上升
 * - 生产环境必须知道"一次Agent调用花多少钱、等多久"
 *
 * 和质量评估的区别：
 * - QualityEvaluator → "结果好不好？"
 * - CostAnalyzer → "花了多少钱、等了多久？"
 * 两者是互补关系，不是替代关系
 */
public class CostAnalyzer {

    // ========== 价格配置（SiliconFlow GLM-5.1） ==========
    // 参考：https://siliconflow.cn/pricing
    // Pro/zai-org/GLM-5.1: 输入 ¥0.5/百万Token, 输出 ¥2/百万Token
    // 简化计算：按平均 ¥1/百万Token（约 ¥0.001/千Token）
    private static final double PRICE_PER_THOUSAND_TOKENS = 0.001;  // ¥/千Token

    // ========== 时间分级阈值 ==========
    private static final long TIME_FAST = 2000;     // <2s → 快
    private static final long TIME_NORMAL = 5000;   // 2-5s → 正常
    private static final long TIME_SLOW = 10000;    // 5-10s → 慢
    // >10s → 很慢

    /**
     * 分析一次Agent执行的成本
     *
     * @param traces TraceListener记录的所有轨迹事件
     * @return CostReport 成本报告
     */
    public CostReport analyze(List<TraceListener.TraceRecord> traces, TokenAccumulator tokenAccumulator) {
        // 1. Token消耗分析（优先使用TokenAccumulator的数据）
        //    TokenAccumulator直接从ChatModel层拦截，数据更准确
        //    TraceListener的tokenCount因为agentic bug可能为-1
        int totalTokens;
        if (tokenAccumulator != null && tokenAccumulator.getTotalTokens() > 0) {
            totalTokens = tokenAccumulator.getTotalTokens();
        } else {
            totalTokens = traces.stream()
                    .filter(t -> t.tokenCount() >= 0)
                    .mapToInt(TraceListener.TraceRecord::tokenCount)
                    .sum();
        }
        double estimatedCostYuan = totalTokens / 1000.0 * PRICE_PER_THOUSAND_TOKENS;

        // 2. 时间消耗分析
        long totalDurationMs = traces.stream()
                .filter(t -> t.durationMs() >= 0)
                .mapToLong(TraceListener.TraceRecord::durationMs)
                .sum();

        // 3. 工具调用时间分布（哪个工具最耗时？）
        Map<String, Long> toolDurationMap = new LinkedHashMap<>();
        for (TraceListener.TraceRecord trace : traces) {
            if ("tool_end".equals(trace.type()) && trace.durationMs() >= 0) {
                // agentName字段格式是"agent/toolName"，取后半部分
                String toolName = trace.agentName().contains("/")
                        ? trace.agentName().substring(trace.agentName().indexOf('/') + 1)
                        : trace.agentName();
                toolDurationMap.merge(toolName, trace.durationMs(), Long::sum);
            }
        }

        // 4. 工具调用次数统计
        Map<String, Integer> toolCallCountMap = new LinkedHashMap<>();
        for (TraceListener.TraceRecord trace : traces) {
            if ("tool_end".equals(trace.type())) {
                String toolName = trace.agentName().contains("/")
                        ? trace.agentName().substring(trace.agentName().indexOf('/') + 1)
                        : trace.agentName();
                toolCallCountMap.merge(toolName, 1, Integer::sum);
            }
        }

        // 5. 工具成功率统计
        Map<String, Double> toolSuccessRateMap = new LinkedHashMap<>();
        for (String toolName : toolCallCountMap.keySet()) {
            int successCount = 0;
            int totalCount = 0;
            for (TraceListener.TraceRecord trace : traces) {
                if ("tool_end".equals(trace.type())) {
                    String tName = trace.agentName().contains("/")
                            ? trace.agentName().substring(trace.agentName().indexOf('/') + 1)
                            : trace.agentName();
                    if (tName.equals(toolName)) {
                        totalCount++;
                        String result = trace.content();
                        if (result != null && !result.contains("❌") && !result.contains("失败")) {
                            successCount++;
                        }
                    }
                }
            }
            toolSuccessRateMap.put(toolName, totalCount > 0 ? (double) successCount / totalCount : 1.0);
        }

        // 6. 每步耗时分级（快/正常/慢/很慢）
        List<StepTiming> stepTimings = new ArrayList<>();
        for (TraceListener.TraceRecord trace : traces) {
            if (trace.durationMs() >= 0) {
                String speedLevel = classifySpeed(trace.durationMs());
                stepTimings.add(new StepTiming(
                        trace.agentName(), trace.type(), trace.durationMs(), speedLevel));
            }
        }

        // 7. 找出最耗时的步骤（瓶颈）
        StepTiming bottleneck = stepTimings.stream()
                .max((a, b) -> Long.compare(a.durationMs, b.durationMs))
                .orElse(null);

        // 8. 时间占比分析
        Map<String, Double> durationRatioMap = new LinkedHashMap<>();
        if (totalDurationMs > 0) {
            for (Map.Entry<String, Long> entry : toolDurationMap.entrySet()) {
                durationRatioMap.put(entry.getKey(), (double) entry.getValue() / totalDurationMs);
            }
        }

        return new CostReport(
                totalTokens, estimatedCostYuan, totalDurationMs,
                toolDurationMap, toolCallCountMap, toolSuccessRateMap,
                stepTimings, bottleneck, durationRatioMap,
                PRICE_PER_THOUSAND_TOKENS
        );
    }

    /**
     * 总成本分析（多次测试汇总，直接传入总Token数）
     */
    public CostReport analyzeWithTotalTokens(List<TraceListener.TraceRecord> traces, int totalTokens) {
        double estimatedCostYuan = totalTokens / 1000.0 * PRICE_PER_THOUSAND_TOKENS;

        long totalDurationMs = traces.stream()
                .filter(t -> t.durationMs() >= 0)
                .mapToLong(TraceListener.TraceRecord::durationMs)
                .sum();

        Map<String, Long> toolDurationMap = new LinkedHashMap<>();
        for (TraceListener.TraceRecord trace : traces) {
            if ("tool_end".equals(trace.type()) && trace.durationMs() >= 0) {
                String toolName = trace.agentName().contains("/")
                        ? trace.agentName().substring(trace.agentName().indexOf('/') + 1)
                        : trace.agentName();
                toolDurationMap.merge(toolName, trace.durationMs(), Long::sum);
            }
        }

        Map<String, Integer> toolCallCountMap = new LinkedHashMap<>();
        for (TraceListener.TraceRecord trace : traces) {
            if ("tool_end".equals(trace.type())) {
                String toolName = trace.agentName().contains("/")
                        ? trace.agentName().substring(trace.agentName().indexOf('/') + 1)
                        : trace.agentName();
                toolCallCountMap.merge(toolName, 1, Integer::sum);
            }
        }

        Map<String, Double> toolSuccessRateMap = new LinkedHashMap<>();
        for (String toolName : toolCallCountMap.keySet()) {
            int successCount = 0;
            int totalCount = 0;
            for (TraceListener.TraceRecord trace : traces) {
                if ("tool_end".equals(trace.type())) {
                    String tName = trace.agentName().contains("/")
                            ? trace.agentName().substring(trace.agentName().indexOf('/') + 1)
                            : trace.agentName();
                    if (tName.equals(toolName)) {
                        totalCount++;
                        String result = trace.content();
                        if (result != null && !result.contains("❌") && !result.contains("失败")) {
                            successCount++;
                        }
                    }
                }
            }
            toolSuccessRateMap.put(toolName, totalCount > 0 ? (double) successCount / totalCount : 1.0);
        }

        List<StepTiming> stepTimings = new ArrayList<>();
        for (TraceListener.TraceRecord trace : traces) {
            if (trace.durationMs() >= 0) {
                String speedLevel = classifySpeed(trace.durationMs());
                stepTimings.add(new StepTiming(trace.agentName(), trace.type(), trace.durationMs(), speedLevel));
            }
        }

        StepTiming bottleneck = stepTimings.stream()
                .max((a, b) -> Long.compare(a.durationMs, b.durationMs))
                .orElse(null);

        Map<String, Double> durationRatioMap = new LinkedHashMap<>();
        if (totalDurationMs > 0) {
            for (Map.Entry<String, Long> entry : toolDurationMap.entrySet()) {
                durationRatioMap.put(entry.getKey(), (double) entry.getValue() / totalDurationMs);
            }
        }

        return new CostReport(
                totalTokens, estimatedCostYuan, totalDurationMs,
                toolDurationMap, toolCallCountMap, toolSuccessRateMap,
                stepTimings, bottleneck, durationRatioMap,
                PRICE_PER_THOUSAND_TOKENS
        );
    }

    private String classifySpeed(long durationMs) {
        if (durationMs < TIME_FAST) return "⚡快";
        if (durationMs < TIME_NORMAL) return "🟢正常";
        if (durationMs < TIME_SLOW) return "🟡慢";
        return "🔴很慢";
    }

    /**
     * 单步耗时记录
     */
    public record StepTiming(
            String name,       // 步骤名称
            String type,       // 步骤类型
            long durationMs,   // 耗时毫秒
            String speedLevel  // 速度分级
    ) {}
}
