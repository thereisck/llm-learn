package com.ck.custom.llmlearn.agents.langgraph;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.AppenderChannel;
import org.bsc.langgraph4j.action.AsyncNodeAction;

import org.bsc.langgraph4j.action.AsyncEdgeAction;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * LangGraph4j 并行执行Demo
 *
 * 核心概念：多个节点同时执行，结果追加到同一个List（AppenderChannel）
 *
 * 场景：智能助手同时查天气+数据库+新闻，汇总后综合回答
 *
 * 图结构：
 * START → aggregator → [并行] → weather/db/news → aggregator → END
 *                          └─────────────────┘
 *
 * 关键API：
 * - AppenderChannel：多个并行节点的输出追加到同一个List
 * - addEdge从同一节点到多个节点 = 自动并行
 */
public class ParallelBranchDemo {

    // State字段（channels定义合并策略）
    public static final String QUERY = "query";
    public static final String WEATHER = "weather";
    public static final String DB_RESULT = "db_result";
    public static final String NEWS = "news";
    public static final String SUMMARY = "summary";

    public static void main(String[] args) throws Exception {
        System.out.println("\n=== LangGraph4j 并行执行Demo ===\n");

        // 1. Schema（普通字段用Channels.base，并行追加用Channels.appender）
        //    并行输出如果想追加到同一个key，用Channels.appender(() -> new ArrayList<>())
        //    这里用独立key演示，每个用base
        Map<String, Channel<?>> schema = Map.of(
                QUERY, Channels.base(() -> ""),
                WEATHER, Channels.base(() -> ""),
                DB_RESULT, Channels.base(() -> ""),
                NEWS, Channels.base(() -> ""),
                SUMMARY, Channels.base(() -> "")
        );

        // 2. 创建StateGraph（传schema）
        StateGraph<AgentState> graph = new StateGraph<>(schema, AgentState::new);

        // 3. 定义节点
        AsyncNodeAction<AgentState> aggregatorNode = (state) -> {
            String query = (String) state.data().get(QUERY);
            System.out.println("[Aggregator] 收到查询: " + query);
            // aggregator节点不需要输出，只是触发并行分支
            return CompletableFuture.completedFuture(Map.of());
        };

        AsyncNodeAction<AgentState> weatherNode = (state) -> {
            System.out.println("[WeatherAgent] 正在查询天气... (模拟耗时1s)");
            try { Thread.sleep(1000); } catch (InterruptedException e) {}
            String result = "上海：晴，28°C，湿度65%";
            System.out.println("[WeatherAgent] 完成: " + result);
            return CompletableFuture.completedFuture(Map.of(WEATHER, result));
        };

        AsyncNodeAction<AgentState> dbNode = (state) -> {
            System.out.println("[DBAgent] 正在查询数据库... (模拟耗时0.5s)");
            try { Thread.sleep(500); } catch (InterruptedException e) {}
            String result = "用户订单数: 1,247 | 总销售额: ¥89,500";
            System.out.println("[DBAgent] 完成: " + result);
            return CompletableFuture.completedFuture(Map.of(DB_RESULT, result));
        };

        AsyncNodeAction<AgentState> newsNode = (state) -> {
            System.out.println("[NewsAgent] 正在查询新闻... (模拟耗时0.8s)");
            try { Thread.sleep(800); } catch (InterruptedException e) {}
            String result = "AI行业：Claude 5发布；多模态能力大幅提升";
            System.out.println("[NewsAgent] 完成: " + result);
            return CompletableFuture.completedFuture(Map.of(NEWS, result));
        };

        AsyncNodeAction<AgentState> summaryNode = (state) -> {
            String weather = (String) state.data().getOrDefault(WEATHER, "无数据");
            String db = (String) state.data().getOrDefault(DB_RESULT, "无数据");
            String news = (String) state.data().getOrDefault(NEWS, "无数据");

            String summary = String.format(
                    "综合报告：\n- 天气：%s\n- 数据：%s\n- 新闻：%s",
                    weather, db, news
            );
            System.out.println("[Summary] 生成综合报告");
            return CompletableFuture.completedFuture(Map.of(SUMMARY, summary));
        };

        // 4. 添加节点
        graph.addNode("aggregator", aggregatorNode);
        graph.addNode("weather", weatherNode);
        graph.addNode("db", dbNode);
        graph.addNode("news", newsNode);
        graph.addNode("summary", summaryNode);

        // 5. 入口边
        graph.addEdge(StateGraph.START, "aggregator");

        // 6. 并行边：aggregator → 3个节点同时执行
        // LangGraph4j：从一个节点出发到多个节点 = 自动并行执行
        graph.addEdge("aggregator", "weather");
        graph.addEdge("aggregator", "db");
        graph.addEdge("aggregator", "news");

        // 7. 汇总边：3个并行节点完成后 → summary
        graph.addEdge("weather", "summary");
        graph.addEdge("db", "summary");
        graph.addEdge("news", "summary");

        // 8. 出口边
        graph.addEdge("summary", StateGraph.END);

        // 9. 编译
        CompiledGraph<AgentState> app = graph.compile(CompileConfig.builder().build());

        // 10. 执行
        long startTime = System.currentTimeMillis();
        Optional<AgentState> result = app.invoke(Map.of(QUERY, "帮我查一下上海天气、销售数据和AI新闻"));
        long elapsed = System.currentTimeMillis() - startTime;

        System.out.println("\n总耗时: " + elapsed + "ms（并行执行应≈串行耗时≈2300ms）");
        result.ifPresent(s -> {
            System.out.println("\n=== 最终结果 ===");
            System.out.println(s.data().get(SUMMARY));
        });

        System.out.println("\n=== Demo完成 ===");
    }
}