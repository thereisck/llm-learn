package com.ck.custom.llmlearn.agents.langgraph;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.AsyncEdgeAction;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * LangGraph4j Checkpoint持久化Demo
 *
 * 核心概念：保存执行状态，暂停恢复，回溯历史
 *
 * 场景：代码审查流水线，审批节点暂停等待人类确认后恢复执行
 *
 * 关键API：
 * - MemorySaver：内存版Checkpoint（生产级用Postgres/MySQL）
 * - CompileConfig.checkpointSaver：编译时传入
 * - interruptBefore/interruptAfter：指定暂停节点
 * - RunnableConfig.threadId：每次执行用不同thread
 * - getState/getStateHistory：查看历史状态
 *
 * 图结构：
 * START → scan → [暂停等待确认] → fix → report → END
 */
public class CheckpointDemo {

    public static final String INPUT = "input";
    public static final String SCAN_RESULT = "scan_result";
    public static final String APPROVED = "approved";
    public static final String FIX_RESULT = "fix_result";
    public static final String REPORT = "report";

    public static void main(String[] args) throws Exception {
        System.out.println("\n=== LangGraph4j Checkpoint持久化Demo ===\n");

        // 1. Schema
        Map<String, Channel<?>> schema = Map.of(
                INPUT, Channels.base(() -> ""),
                SCAN_RESULT, Channels.base(() -> ""),
                APPROVED, Channels.base(() -> "no"),
                FIX_RESULT, Channels.base(() -> ""),
                REPORT, Channels.base(() -> "")
        );

        // 2. StateGraph
        StateGraph<AgentState> graph = new StateGraph<>(schema, AgentState::new);

        // 3. 节点
        AsyncNodeAction<AgentState> scanNode = (state) -> {
            String input = (String) state.data().get(INPUT);
            String result = "扫描结果：发现3个安全漏洞(SQL注入/路径穿越/XSS)";
            System.out.println("[Scan] " + result);
            return CompletableFuture.completedFuture(Map.of(SCAN_RESULT, result));
        };

        AsyncNodeAction<AgentState> fixNode = (state) -> {
            String approved = (String) state.data().getOrDefault(APPROVED, "no");
            if (!approved.equals("yes")) {
                System.out.println("[Fix] ⛔ 未获批准，跳过修复");
                return CompletableFuture.completedFuture(Map.of(FIX_RESULT, "未修复"));
            }
            String result = "修复完成：3个漏洞已全部修复";
            System.out.println("[Fix] ✅ " + result);
            return CompletableFuture.completedFuture(Map.of(FIX_RESULT, result));
        };

        AsyncNodeAction<AgentState> reportNode = (state) -> {
            String scan = (String) state.data().get(SCAN_RESULT);
            String fix = (String) state.data().get(FIX_RESULT);
            String report = "最终报告：\n扫描：" + scan + "\n修复：" + fix;
            System.out.println("[Report] " + report);
            return CompletableFuture.completedFuture(Map.of(REPORT, report));
        };

        // 4. 构建图
        graph.addNode("scan", scanNode);
        graph.addNode("fix", fixNode);
        graph.addNode("report", reportNode);

        graph.addEdge(StateGraph.START, "scan");
        graph.addEdge("scan", "fix");
        graph.addEdge("fix", "report");
        graph.addEdge("report", StateGraph.END);

        // 5. Checkpoint配置——⭐ 关键！
        //    MemorySaver = 内存版持久化（生产用Postgres/MySQL/Redis）
        //    interruptBefore("fix") = fix节点执行前暂停，等人类确认
        MemorySaver saver = new MemorySaver();
        CompileConfig config = CompileConfig.builder()
                .checkpointSaver(saver)
                .interruptBefore("fix")  // ⭐ 在fix之前暂停！
                .build();

        CompiledGraph<AgentState> app = graph.compile(config);

        // ========================================================
        // 6. 第一次执行：会停在fix节点之前
        // ========================================================
        String threadId = "thread-security-review-1";
        RunnableConfig runConfig = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        System.out.println("--- 第一次执行：扫描后暂停 ---");
        Optional<AgentState> result1 = app.invoke(Map.of(INPUT, "审查代码安全性"), runConfig);

        // ⚠️ 执行被暂停，result1是暂停前的状态
        System.out.println("\n⚠️ 执行暂停在fix节点前，等待人类确认...");

        // 查看当前状态
        var state1 = app.getState(runConfig);
        System.out.println("当前State: " + state1.state().data());
        System.out.println("下一步节点: " + state1.next());

        // ========================================================
        // 7. 人类确认：更新State，然后恢复执行
        // ========================================================
        System.out.println("\n--- 人类审批：批准修复 ---");
        // 更新State：批准修复（两参数版本，不带asNode）
        app.updateState(runConfig, Map.of(APPROVED, "yes"));

        // 查看更新后的状态
        var state2 = app.getState(runConfig);
        System.out.println("更新后State: " + state2.state().data());

        // ========================================================
        // 8. 恢复执行：从暂停点继续
        // ========================================================
        System.out.println("\n--- 恢复执行：修复→报告 ---");
        Optional<AgentState> result2 = app.invoke(Map.of(), runConfig);  // 空Map = 从checkpoint恢复

        result2.ifPresent(s -> {
            System.out.println("\n=== 最终结果 ===");
            System.out.println(s.data().get(REPORT));
        });

        // ========================================================
        // 9. 查看历史状态（回溯）
        // ========================================================
        System.out.println("\n--- 状态历史回溯 ---");
        var history = app.getStateHistory(runConfig);
        history.forEach(h -> {
            System.out.println("Step: " + h.next() + " | State: " + h.state().data());
        });

        System.out.println("\n=== Demo完成 ===");

        // ========================================================
        // 10. 对比总结
        // ========================================================
        System.out.println("\n📊 Checkpoint vs 无Checkpoint：");
        System.out.println("无Checkpoint：一次性跑完，无法暂停恢复");
        System.out.println("有Checkpoint：");
        System.out.println("  - interruptBefore → 人类审批场景（金融/客服）");
        System.out.println("  - interruptAfter → 查看中间结果后再决定");
        System.out.println("  - getState/getStateHistory → 调试+回溯");
        System.out.println("  - updateState → 人类修改Agent决策");
        System.out.println("  - MemorySaver=内存版，生产用Postgres/MySQL");
    }
}