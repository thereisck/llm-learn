package com.ck.custom.llmlearn.agents.langgraph;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.AsyncEdgeAction;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * LangGraph4j StateGraph Demo（最简版）
 *
 * 从编译错误推断：
 * - StateGraph直接new，不需要builder
 * - AsyncEdgeAction返回String（节点名）
 *
 * 图结构：
 * START → classifier → [条件边] → coder/text/math → END
 */
public class ConditionalGraphDemo {

    public static final String INPUT = "input";
    public static final String CATEGORY = "category";
    public static final String OUTPUT = "output";

    public static void main(String[] args) throws Exception {
        System.out.println("\n=== LangGraph4j 条件分支Demo ===\n");

        // 1. StateGraph需要schema（Map of Channel）或factory
        //    使用AgentState::new作为最简factory
        StateGraph<AgentState> graph = new StateGraph<>(AgentState::new);

        // 2. 定义节点
        AsyncNodeAction<AgentState> classifierNode = (state) -> {
            String input = (String) state.data().get(INPUT);
            String category = input.contains("代码") || input.contains("算法") ? "code"
                    : input.contains("文案") || input.contains("文章") ? "text"
                    : input.contains("数学") || input.contains("计算") ? "math"
                    : "unknown";

            System.out.println("[Classifier] 分类结果: " + category);
            return CompletableFuture.completedFuture(Map.of(CATEGORY, category));
        };

        AsyncNodeAction<AgentState> coderNode = (state) -> {
            System.out.println("[Coder] 处理代码请求...");
            return CompletableFuture.completedFuture(Map.of(OUTPUT, "已生成排序算法代码"));
        };

        AsyncNodeAction<AgentState> writerNode = (state) -> {
            System.out.println("[Writer] 处理文案请求...");
            return CompletableFuture.completedFuture(Map.of(OUTPUT, "已创作技术文案"));
        };

        AsyncNodeAction<AgentState> mathNode = (state) -> {
            System.out.println("[MathAgent] 处理数学请求...");
            return CompletableFuture.completedFuture(Map.of(OUTPUT, "计算结果为42"));
        };

        // 3. 添加节点
        graph.addNode("classifier", classifierNode);
        graph.addNode("coder", coderNode);
        graph.addNode("writer", writerNode);
        graph.addNode("math", mathNode);

        // 4. 入口边
        graph.addEdge(StateGraph.START, "classifier");

        // 5. 条件边路由（返回String）
        AsyncEdgeAction<AgentState> router = (state) -> {
            String category = (String) state.data().get(CATEGORY);
            System.out.println("[Router] 路由到: " + category);

            switch (category) {
                case "code": return CompletableFuture.completedFuture("coder");
                case "text": return CompletableFuture.completedFuture("writer");
                case "math": return CompletableFuture.completedFuture("math");
                default: return CompletableFuture.completedFuture(StateGraph.END);
            }
        };

        // 6. 添加条件边（含映射表）
        graph.addConditionalEdges("classifier", router, Map.of(
                "coder", "coder", "writer", "writer", "math", "math",
                StateGraph.END, StateGraph.END
        ));

        // 7. 出口边
        graph.addEdge("coder", StateGraph.END);
        graph.addEdge("writer", StateGraph.END);
        graph.addEdge("math", StateGraph.END);

        // 8. 编译
        CompiledGraph<AgentState> app = graph.compile(CompileConfig.builder().build());

        // 9. 执行测试
        System.out.println("--- 测试1：代码请求 ---");
        Optional<AgentState> r1 = app.invoke(Map.of(INPUT, "帮我写个快速排序算法"));
        r1.ifPresent(s -> System.out.println("输出: " + s.data().get(OUTPUT)));

        System.out.println("\n--- 测试2：文案请求 ---");
        Optional<AgentState> r2 = app.invoke(Map.of(INPUT, "写篇AI技术文章"));
        r2.ifPresent(s -> System.out.println("输出: " + s.data().get(OUTPUT)));

        System.out.println("\n--- 测试3：数学请求 ---");
        Optional<AgentState> r3 = app.invoke(Map.of(INPUT, "求斐波那契数列第10项"));
        r3.ifPresent(s -> System.out.println("输出: " + s.data().get(OUTPUT)));

        System.out.println("\n=== Demo完成 ===");
    }
}