package com.ck.custom.llmlearn.agents.langgraph;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.action.AsyncNodeAction;

import org.bsc.langgraph4j.action.AsyncEdgeAction;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * LangGraph4j Subgraph嵌套Demo
 *
 * 核心概念：子图作为节点，封装复杂逻辑
 *
 * 场景：代码审查流水线
 *   主图：总控调度（接收请求→分派子图→汇总报告）
 *   子图：安全审查（扫描→评级→建议修复）
 *
 * 关键API：
 * - addNode("子图名", compiledSubGraph) 把编译后的子图作为节点
 * - 子图有自己的State，主图有自己的State
 * - 子图输出映射到主图State
 *
 * 主图结构：
 * START → dispatcher → [security_subgraph] → reporter → END
 *
 * 子图结构（security_subgraph内部）：
 * START → scanner → rater → suggester → END
 */
public class SubgraphDemo {

    // 主图State字段
    public static final String INPUT = "input";
    public static final String DISPATCH_RESULT = "dispatch_result";
    public static final String SECURITY_RESULT = "security_result";
    public static final String REPORT = "report";

    // 子图State字段
    public static final String CODE = "code";
    public static final String SCAN_OUTPUT = "scan_output";
    public static final String RATING = "rating";
    public static final String SUGGESTION = "suggestion";

    public static void main(String[] args) throws Exception {
        System.out.println("\n=== LangGraph4j Subgraph嵌套Demo ===\n");

        // ========================================================
        // 1. 构建子图：安全审查流水线
        // ========================================================
        System.out.println("--- 构建子图：security_subgraph ---");

        Map<String, Channel<?>> subSchema = Map.of(
                CODE, Channels.base(() -> ""),
                SCAN_OUTPUT, Channels.base(() -> ""),
                RATING, Channels.base(() -> ""),
                SUGGESTION, Channels.base(() -> "")
        );

        StateGraph<AgentState> subGraph = new StateGraph<>(subSchema, AgentState::new);

        // 子图节点
        AsyncNodeAction<AgentState> scannerNode = (state) -> {
            String code = (String) state.data().get(CODE);
            String scanResult = "扫描发现：SQL注入风险(high) + XSS风险(medium) + 路径穿越风险(low)";
            System.out.println("[SubGraph-Scanner] " + scanResult);
            return CompletableFuture.completedFuture(Map.of(SCAN_OUTPUT, scanResult));
        };

        AsyncNodeAction<AgentState> raterNode = (state) -> {
            String scan = (String) state.data().get(SCAN_OUTPUT);
            String rating = "安全评级：C级（存在高风险漏洞，需要修复）";
            System.out.println("[SubGraph-Rater] " + rating);
            return CompletableFuture.completedFuture(Map.of(RATING, rating));
        };

        AsyncNodeAction<AgentState> suggesterNode = (state) -> {
            String rating = (String) state.data().get(RATING);
            String suggestion = "建议：使用PreparedStatement替代Statement + 转义HTML输出 + 验证文件路径";
            System.out.println("[SubGraph-Suggester] " + suggestion);
            return CompletableFuture.completedFuture(Map.of(SUGGESTION, suggestion));
        };

        subGraph.addNode("scanner", scannerNode);
        subGraph.addNode("rater", raterNode);
        subGraph.addNode("suggester", suggesterNode);

        subGraph.addEdge(StateGraph.START, "scanner");
        subGraph.addEdge("scanner", "rater");
        subGraph.addEdge("rater", "suggester");
        subGraph.addEdge("suggester", StateGraph.END);

        // 编译子图
        CompiledGraph<AgentState> compiledSubGraph = subGraph.compile(CompileConfig.builder().build());
        System.out.println("子图编译完成 ✓");

        // ========================================================
        // 2. 构建主图：总控调度
        // ========================================================
        System.out.println("\n--- 构建主图：main_graph ---");

        Map<String, Channel<?>> mainSchema = Map.of(
                INPUT, Channels.base(() -> ""),
                DISPATCH_RESULT, Channels.base(() -> ""),
                SECURITY_RESULT, Channels.base(() -> ""),
                REPORT, Channels.base(() -> "")
        );

        StateGraph<AgentState> mainGraph = new StateGraph<>(mainSchema, AgentState::new);

        // 主图节点
        AsyncNodeAction<AgentState> dispatcherNode = (state) -> {
            String input = (String) state.data().get(INPUT);
            String dispatch = "分派到安全审查子图";
            System.out.println("[Main-Dispatcher] " + dispatch);
            return CompletableFuture.completedFuture(Map.of(DISPATCH_RESULT, dispatch));
        };

        AsyncNodeAction<AgentState> reporterNode = (state) -> {
            String security = (String) state.data().getOrDefault(SECURITY_RESULT, "无安全审查结果");
            String report = "代码审查最终报告：\n" + security;
            System.out.println("[Main-Reporter] 生成最终报告");
            return CompletableFuture.completedFuture(Map.of(REPORT, report));
        };

        // ⭐ 关键：子图作为节点！
        mainGraph.addNode("dispatcher", dispatcherNode);
        mainGraph.addNode("security_subgraph", compiledSubGraph);  // ⭐ 子图=节点
        mainGraph.addNode("reporter", reporterNode);

        mainGraph.addEdge(StateGraph.START, "dispatcher");
        mainGraph.addEdge("dispatcher", "security_subgraph");
        mainGraph.addEdge("security_subgraph", "reporter");
        mainGraph.addEdge("reporter", StateGraph.END);

        // 编译主图
        CompiledGraph<AgentState> app = mainGraph.compile(CompileConfig.builder().build());

        // ========================================================
        // 3. 执行主图（子图自动被触发）
        // ========================================================
        System.out.println("\n--- 执行主图 ---");
        Optional<AgentState> result = app.invoke(Map.of(
                INPUT, "审查这段代码的安全性"
        ));

        result.ifPresent(s -> {
            System.out.println("\n=== 最终结果 ===");
            System.out.println(s.data().get(REPORT));
        });

        // ========================================================
        // 4. 对比总结
        // ========================================================
        System.out.println("\n📊 Subgraph vs 平铺所有节点：");
        System.out.println("平铺：9个节点全部在主图 → 难维护、状态共享混乱");
        System.out.println("Subgraph：");
        System.out.println("  - 子图有独立State → 不污染主图");
        System.out.println("  - 子图可复用 → 不同项目用同一个安全审查子图");
        System.out.println("  - 主图只关心子图输出 → 简洁");
        System.out.println("  - Java直觉：子图=微服务，主图=API Gateway");

        System.out.println("\n=== Demo完成 ===");
    }
}