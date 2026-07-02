package com.ck.custom.llmlearn.agents.monitoring;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Week6 Day6 Demo: Agent监控与调试
 *
 * 完整三件套：
 * 1. TraceListener → 执行轨迹追踪（7个hook全覆盖）
 * 2. AgentQualityEvaluator → 质量评估（5维度加权评分）
 * 3. CostAnalyzer → 成本分析（Token+时间+瓶颈）
 *
 * 框架内置：AgentMonitor + HtmlReportGenerator → 可视化
 */
@Slf4j
public class MonitoringDemo {

    // 保存所有测试的traces，用于最终的成本总分析
    private static final List<TraceListener.TraceRecord> allTraces = new ArrayList<>();

    public static void main(String[] args) {
        log.info("==================================================");
        log.info("Week6 Day6: Agent监控与调试 Demo");
        log.info("==================================================");

        // 1. 创建ChatModel（注入TokenAccumulator到ChatModel层，绕过agentic bug）
        TokenAccumulator tokenAccumulator = new TokenAccumulator();
        ChatModel chatModel = createChatModel(tokenAccumulator);

        // 2. 创建工具实例
        com.ck.custom.llmlearn.agents.smart_assistant.SmartAssistantTools tools =
                new com.ck.custom.llmlearn.agents.smart_assistant.SmartAssistantTools();

        // 3. 创建四件套
        TraceListener traceListener = new TraceListener();
        AgentMonitor monitor = new AgentMonitor();
        AgentQualityEvaluator evaluator = new AgentQualityEvaluator();
        CostAnalyzer costAnalyzer = new CostAnalyzer();

        // 4. 创建带监控的Agent
        MonitoredAssistant assistant = AgenticServices.agentBuilder(MonitoredAssistant.class)
                .chatModel(chatModel)
                .tools(tools)
                .listener(traceListener)
                .listener(monitor)
                .build();

        // ========== 执行测试场景 ==========

        // 测试1：纯对话
        log.info("\n--- 测试1: 纯对话 ---");
        traceListener.getTraces().clear();
        tokenAccumulator.reset();
        String result1 = assistant.chat("你好，请用一句话介绍你自己");
        log.info("回答: {}", result1);
        allTraces.addAll(traceListener.getTraces());
        int test1Tokens = tokenAccumulator.getTotalTokens();
        log.info("🔢 Token统计: {}", tokenAccumulator.summary());

        QualityReport report1 = evaluator.evaluate(traceListener.getTraces(), 2);
        CostReport cost1 = costAnalyzer.analyze(traceListener.getTraces(), tokenAccumulator);
        log.info("\n" + report1.toTextReport());
        log.info("\n" + cost1.toTextReport());

        // 测试2：单工具调用
        log.info("\n--- 测试2: 单工具调用 ---");
        traceListener.getTraces().clear();
        tokenAccumulator.reset();
        String result2 = assistant.chat("北京今天天气怎么样？");
        log.info("回答: {}", result2);
        allTraces.addAll(traceListener.getTraces());
        int test2Tokens = tokenAccumulator.getTotalTokens();
        log.info("🔢 Token统计: {}", tokenAccumulator.summary());

        QualityReport report2 = evaluator.evaluate(traceListener.getTraces(), 4);
        CostReport cost2 = costAnalyzer.analyze(traceListener.getTraces(), tokenAccumulator);
        log.info("\n" + report2.toTextReport());
        log.info("\n" + cost2.toTextReport());

        // 测试3：多工具串联
        log.info("\n--- 测试3: 多工具串联 ---");
        traceListener.getTraces().clear();
        tokenAccumulator.reset();
        String result3 = assistant.chat("上海和北京温度差是多少？");
        log.info("回答: {}", result3);
        allTraces.addAll(traceListener.getTraces());
        int test3Tokens = tokenAccumulator.getTotalTokens();
        log.info("🔢 Token统计: {}", tokenAccumulator.summary());

        QualityReport report3 = evaluator.evaluate(traceListener.getTraces(), 6);
        CostReport cost3 = costAnalyzer.analyze(traceListener.getTraces(), tokenAccumulator);
        log.info("\n" + report3.toTextReport());
        log.info("\n" + cost3.toTextReport());

        // ========== 汇总输出 ==========

        // 7. 轨迹总报告（三次测试的完整轨迹）
        // 重建traceListener的traces用于总报告
        TraceListener totalTraceListener = new TraceListener();
        totalTraceListener.getTraces().addAll(allTraces);
        log.info("\n" + totalTraceListener.generateTraceReport());

        // 8. 总成本分析（三次测试汇总）
        // 用三次测试的Token总量做总分析
        tokenAccumulator.reset();
        tokenAccumulator.getTotalTokens(); // reset后为0，手动设置
        // 最简单的方式：创建一个新的TokenAccumulator，手动设置总Token
        int totalTokensAll = test1Tokens + test2Tokens + test3Tokens;
        TokenAccumulator totalTokenAcc = new TokenAccumulator();
        // 没法直接设totalTokens（AtomicInteger），但CostAnalyzer会从traces的tokenCount汇总
        // 所以总分析直接用null，让CostAnalyzer从tokenCount取（但traces的tokenCount也是-1）
        // 最干脆的方式：改CostAnalyzer让它接受一个直接的totalTokens参数
        CostReport totalCost = costAnalyzer.analyzeWithTotalTokens(allTraces, totalTokensAll);
        log.info("\n" + totalCost.toTextReport());

        // 9. HTML可视化报告
        try {
            String htmlReport = HtmlReportGenerator.generateReport(monitor);
            String reportPath = "/tmp/agent-monitor-report.html";
            java.nio.file.Files.writeString(Path.of(reportPath), htmlReport);
            log.info("📊 HTML报告已生成: {}", reportPath);
        } catch (Exception e) {
            log.warn("HTML报告生成失败: {}", e.getMessage());
        }

        // 10. AgentMonitor统计
        log.info("\n📈 AgentMonitor统计:");
        log.info("  成功执行: {}次", monitor.successfulExecutions().size());
        log.info("  失败执行: {}次", monitor.failedExecutions().size());
        for (var execution : monitor.successfulExecutions()) {
            AgentInvocation invocation = execution.topLevelInvocations();
            log.info("  - Agent: {} | 耗时: {} | Token: {}",
                    invocation.agent() != null ? invocation.agent().name() : "unknown",
                    invocation.duration(),
                    invocation.totalTokenCount());
        }

        // 11. 三次测试的对比总结
        log.info("\n🏆 三次测试综合对比:");
        log.info("  测试1(纯对话): {} | {}", report1.summary(), cost1.summary());
        log.info("  测试2(单工具): {} | {}", report2.summary(), cost2.summary());
        log.info("  测试3(多工具): {} | {}", report3.summary(), cost3.summary());
        log.info("  总成本汇总: {}", totalCost.summary());

        log.info("\n==================================================");
        log.info("🎉 Week6 Day6 Demo完成！");
        log.info("==================================================");
    }

    public static ChatModel createChatModel(TokenAccumulator tokenAccumulator) {
        String apiKey = "sk-kemgxaacaqdehipgoywavavmpaiazkocfjyixcvsnslmeycv";
        if (apiKey == null || apiKey.isEmpty()) apiKey = "";

        return OpenAiChatModel.builder()
                .baseUrl("https://api.siliconflow.cn/v1")
                .apiKey(apiKey)
                .modelName("Pro/zai-org/GLM-5.1")
                .timeout(java.time.Duration.ofSeconds(120))
                .logRequests(true)
                .logResponses(true)
                .listeners(tokenAccumulator)   // ← 注入TokenAccumulator（关键！）
                .build();
    }
}