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
 * LangGraph4j 循环边Demo —— 对比 Python looping_workflow.py
 *
 * 核心范式转换：
 * Python: while score < threshold { editor → reviewer }
 * LangGraph4j: reviewer → [条件边] → score<7回editor | score>=7到END
 *
 * 图结构：
 * START → editor → reviewer → [条件边] → editor(循环) | END(达标)
 */
public class LoopingGraphDemo {

    public static final String INPUT = "input";
    public static final String DRAFT = "draft";           // 当前稿件
    public static final String SCORE = "score";            // 质量评分
    public static final String SUGGESTIONS = "suggestions"; // 修改建议
    public static final String FINAL_OUTPUT = "final_output";
    public static final String ITERATION = "iteration";    // 当前迭代次数

    // 模拟阈值（对比Python的threshold=7）
    private static final int THRESHOLD = 7;

    public static void main(String[] args) throws Exception {
        System.out.println("\n=== LangGraph4j 循环边Demo ===");
        System.out.println("（对比 Python looping_workflow.py：Editor→Reviewer→循环直到score>=7）\n");

        // 1. 创建StateGraph
        StateGraph<AgentState> graph = new StateGraph<>(AgentState::new);

        // ========================================================
        // 2. Editor节点：模拟编辑Agent
        //    Python版：EditorAgent.run(original, suggestions)
        //    LangGraph4j版：读draft和suggestions，输出新draft
        // ========================================================
        AsyncNodeAction<AgentState> editorNode = (state) -> {
            int iteration = state.data().containsKey(ITERATION)
                    ? (int) state.data().get(ITERATION) : 0;
            iteration++;

            String draft;
            if (iteration == 1) {
                // 第一次：从input生成初始draft
                String input = (String) state.data().get(INPUT);
                draft = "初稿(v1): 关于\"" + input + "\"的文章。内容比较粗糙，需要打磨。";
                System.out.println("[Editor] 第" + iteration + "次迭代 → 写初稿");
            } else {
                // 后续：根据reviewer的建议改进
                String oldDraft = (String) state.data().get(DRAFT);
                String suggestions = (String) state.data().get(SUGGESTIONS);
                draft = "改进稿(v" + iteration + "): " + oldDraft + " | 已根据建议\"" + suggestions + "\"优化";
                System.out.println("[Editor] 第" + iteration + "次迭代 → 改进稿件");
            }

            System.out.println("[Editor] draft: " + draft.substring(0, Math.min(50, draft.length())) + "...");

            return CompletableFuture.completedFuture(Map.of(
                    DRAFT, draft,
                    ITERATION, iteration
            ));
        };

        // ========================================================
        // 3. Reviewer节点：模拟审稿Agent
        //    Python版：ReviewerAgent.run(draft) → {score, issues, suggestions}
        //    LangGraph4j版：读draft，输出score和suggestions
        // ========================================================
        AsyncNodeAction<AgentState> reviewerNode = (state) -> {
            int iteration = state.data().containsKey(ITERATION)
                    ? (int) state.data().get(ITERATION) : 1;

            // 模拟评分逻辑：随迭代次数递增（模拟LLM改进效果）
            int score = Math.min(10, 4 + iteration * 2);  // v1=6, v2=8, v3=10

            String suggestions;
            if (score < THRESHOLD) {
                suggestions = "需要增加数据支撑、减少抽象描述、添加具体案例";
            } else {
                suggestions = "质量达标，无需修改";
            }

            System.out.println("[Reviewer] 评分: " + score + "/10 | 建议: " + suggestions);

            return CompletableFuture.completedFuture(Map.of(
                    SCORE, score,
                    SUGGESTIONS, suggestions
            ));
        };

        // ========================================================
        // 4. Finalizer节点：最终输出格式化（达标后执行）
        // ========================================================
        AsyncNodeAction<AgentState> finalizerNode = (state) -> {
            String draft = (String) state.data().get(DRAFT);
            int score = (int) state.data().get(SCORE);
            int iteration = (int) state.data().get(ITERATION);

            String output = "✅ 最终稿件(迭代" + iteration + "次, 评分" + score + "/10):\n" + draft;

            System.out.println("[Finalizer] 输出最终结果");
            return CompletableFuture.completedFuture(Map.of(FINAL_OUTPUT, output));
        };

        // ========================================================
        // 5. 构建图结构
        //    这是最核心的部分——对比Python的手写while循环
        // ========================================================

        // 添加节点
        graph.addNode("editor", editorNode);
        graph.addNode("reviewer", reviewerNode);
        graph.addNode("finalizer", finalizerNode);

        // 入口边
        graph.addEdge(StateGraph.START, "editor");

        // 固定边：editor → reviewer（编辑完→送审）
        graph.addEdge("editor", "reviewer");

        // ⭐⭐⭐ 核心差异：条件边实现循环 ⭐⭐⭐
        // Python: while score < threshold { ... }
        // LangGraph4j: reviewer → [条件边] → 不达标回editor | 达标到finalizer
        AsyncEdgeAction<AgentState> qualityRouter = (state) -> {
            int score = (int) state.data().get(SCORE);
            int iteration = (int) state.data().get(ITERATION);

            if (score >= THRESHOLD) {
                System.out.println("[QualityRouter] 评分" + score + ">=阈值" + THRESHOLD + " → 路由到finalizer（达标退出）");
                return CompletableFuture.completedFuture("finalizer");
            } else {
                System.out.println("[QualityRouter] 评分" + score + "<阈值" + THRESHOLD + " → 路由到editor（循环改进）");
                return CompletableFuture.completedFuture("editor");  // ⭐ 回到editor！这就是循环边
            }
        };

        // 条件边：reviewer → qualityRouter
        graph.addConditionalEdges("reviewer", qualityRouter, Map.of(
                "editor", "editor",    // 循环边：不达标→回到editor
                "finalizer", "finalizer" // 出口边：达标→到finalizer
        ));

        // 出口边
        graph.addEdge("finalizer", StateGraph.END);

        // ========================================================
        // 6. 编译（设置recursionLimit防止无限循环）
        //    Python版靠max_iterations=3控制
        //    LangGraph4j靠recursionLimit控制（默认25）
        // ========================================================
        CompileConfig config = CompileConfig.builder()
                .recursionLimit(10)  // 最大10次循环（安全阀）
                .build();

        CompiledGraph<AgentState> app = graph.compile(config);

        // ========================================================
        // 7. 执行测试
        // ========================================================
        System.out.println("--- 测试：写一篇AI技术文章 ---");
        Optional<AgentState> result = app.invoke(Map.of(INPUT, "AI Agent架构设计"));

        result.ifPresent(s -> {
            System.out.println("\n" + s.data().get(FINAL_OUTPUT));
            System.out.println("总迭代次数: " + s.data().get(ITERATION));
            System.out.println("最终评分: " + s.data().get(SCORE));
        });

        System.out.println("\n=== Demo完成 ===");

        // ========================================================
        // 8. 对比总结
        // ========================================================
        System.out.println("\n📊 对比Python looping_workflow.py vs LangGraph4j:");
        System.out.println("Python: while score < threshold and iter < max_iterations:");
        System.out.println("  edited = editor.run(original, suggestions)");
        System.out.println("  result = reviewer.run(edited)");
        System.out.println("  score = result['score']");
        System.out.println();
        System.out.println("LangGraph4j: addConditionalEdges('reviewer', router)");
        System.out.println("  router: score >= threshold → 'finalizer' | score < threshold → 'editor'");
        System.out.println("  循环是图的边，不是代码的while循环！");
    }
}