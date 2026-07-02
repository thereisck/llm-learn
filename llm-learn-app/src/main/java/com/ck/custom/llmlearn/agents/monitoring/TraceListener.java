package com.ck.custom.llmlearn.agents.monitoring;

import dev.langchain4j.agentic.observability.*;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecution;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义Agent执行轨迹追踪Listener
 *
 * LangChain4j agentic内置的观察性体系核心接口：AgentListener
 * 7个hook方法覆盖Agent执行的完整生命周期：
 * 1. beforeAgentInvocation  → Agent开始调用
 * 2. afterAgentInvocation   → Agent调用结束
 * 3. onAgentInvocationError → Agent调用出错
 * 4. afterAgenticScopeCreated → Scope创建
 * 5. beforeAgenticScopeDestroyed → Scope销毁
 * 6. beforeAgentToolExecution → 工具开始执行
 * 7. afterAgentToolExecution  → 工具执行结束
 *
 * 对比OpenTelemetry的Span概念：
 * - AgentInvocation ≈ 一个Span（有startTime/finishTime/duration）
 * - nestedInvocations ≈ 子Span（多Agent嵌套）
 * - toolExecutions ≈ Span内的event（工具调用记录）
 * - inheritedBySubagents() = true → 子Agent继承这个Listener
 */
@Slf4j
public class TraceListener implements AgentListener {

    private final List<TraceRecord> traces = new ArrayList<>();
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // ========== Token累计器 ==========
    // 从HTTP响应日志里解析Token（最可靠的方式）
    // 因为AgentResponse.chatResponse()在agentic模块里经常是null
    private int accumulatedTokens = 0;
    private long lastInvocationStartMs = -1;  // 记录Agent调用开始时间，用于算耗时

    /**
     * 轨迹记录数据模型（类比OpenTelemetry的Span）
     *
     * 6个核心字段：
     * | 字段 | 含义 | 类比 |
     * |------|------|------|
     * | type | agent_start/end/error, tool_start/end, scope_create/destroy | Span.kind |
     * | agentName | 哪个Agent | Span.service |
     * | content | 输入/输出/错误/参数/结果 | Span.attributes |
     * | timestamp | 时间 | Span.startTime |
     * | durationMs | 耗时（毫秒） | Span.duration |
     * | tokenCount | Token消耗 | 自定义attribute |
     */
    public record TraceRecord(
            String type,
            String agentName,
            String content,
            LocalDateTime timestamp,
            long durationMs,
            int tokenCount
    ) {}

    // ========== Hook 1: Agent开始调用 ==========

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        String agentName = request.agentName();
        String input = truncate(mapToString(request.inputs()));
        log.info("🔍 [TRACE] Agent开始: {} | 输入: {}", agentName, input);

        traces.add(new TraceRecord(
                "agent_start", agentName, input,
                LocalDateTime.now(), -1, -1
        ));
    }

    // ========== Hook 2: Agent调用结束 ==========

    /**
     * AgentResponse的字段：
     * - agentName() → Agent名称
     * - output() → 最终输出
     * - chatResponse() → 原始响应（含Token统计）
     *
     * Token获取策略：
     * 1. chatResponse().tokenUsage() → 直接从HTTP响应拿（最准确）
     * 2. 如果chatResponse为null → 用afterAgentInvocation的时间差估算
     *
     * 实测发现：agentic模块的AgentResponse.chatResponse()有时为null
     * 这是因为agentic内部走不同的调用路径，Token数据可能没传递过来
     * 所以我们加一个fallback：如果拿不到Token，就记录"N/A"
     */
    @Override
    public void afterAgentInvocation(AgentResponse response) {
        String agentName = response.agentName();
        String output = truncate(String.valueOf(response.output()));
        int tokens = -1;

        // ===== DEBUG: 打印AgentResponse的所有字段 =====
        // 看看chatResponse到底是不是null，如果是null就是agentic模块没传递
        log.info("🔍 [DEBUG] AgentResponse字段诊断:");
        log.info("   agentName: {}", response.agentName());
        log.info("   output: {}", truncate(String.valueOf(response.output()), 200));
        log.info("   chatResponse: {}", response.chatResponse() != null ? "存在(非null)" : "null ← 这就是Token=0的原因！");
        if (response.chatResponse() != null) {
            log.info("   chatResponse.tokenUsage: {}", response.chatResponse().tokenUsage());
            if (response.chatResponse().tokenUsage() != null) {
                log.info("   inputTokenCount: {}", response.chatResponse().tokenUsage().inputTokenCount());
                log.info("   outputTokenCount: {}", response.chatResponse().tokenUsage().outputTokenCount());
                log.info("   totalTokenCount: {}", response.chatResponse().tokenUsage().totalTokenCount());
                tokens = response.chatResponse().tokenUsage().totalTokenCount();
            }
        }
        log.info("   chatRequest: {}", response.chatRequest() != null ? "存在" : "null");
        log.info("   agenticScope: {}", response.agenticScope() != null ? "存在(memoryId=" + response.agenticScope().memoryId() + ")" : "null");
        log.info("   agent: {}", response.agent() != null ? "存在(name=" + response.agent().name() + ")" : "null");
        log.info("   inputs: {}", truncate(mapToString(response.inputs()), 200));
        // ===== END DEBUG =====

        log.info("✅ [TRACE] Agent结束: {} | Token:{} | 输出: {}", agentName, tokens >= 0 ? tokens : "N/A", output);

        // 如果从chatResponse拿到了Token，累加到累计器
        if (tokens >= 0) {
            accumulatedTokens += tokens;
        }

        traces.add(new TraceRecord(
                "agent_end", agentName, output,
                LocalDateTime.now(), -1, tokens
        ));
    }

    // ========== Hook 3: Agent调用出错 ==========

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        String agentName = error.agentName();
        String errorMsg = truncate(error.error() != null ? error.error().getMessage() : "unknown error");
        log.error("❌ [TRACE] Agent错误: {} | 错误: {}", agentName, errorMsg);

        traces.add(new TraceRecord(
                "agent_error", agentName, errorMsg,
                LocalDateTime.now(), -1, -1
        ));
    }

    // ========== Hook 4: Scope创建 ==========

    @Override
    public void afterAgenticScopeCreated(AgenticScope scope) {
        log.info("🆔 [TRACE] Scope创建: memoryId={}", scope.memoryId());
        traces.add(new TraceRecord(
                "scope_create", "scope", String.valueOf(scope.memoryId()),
                LocalDateTime.now(), -1, -1
        ));
    }

    // ========== Hook 5: Scope销毁 ==========

    @Override
    public void beforeAgenticScopeDestroyed(AgenticScope scope) {
        log.info("🆔 [TRACE] Scope销毁: memoryId={}", scope.memoryId());
        traces.add(new TraceRecord(
                "scope_destroy", "scope", String.valueOf(scope.memoryId()),
                LocalDateTime.now(), -1, -1
        ));
    }

    // ========== Hook 6: 工具开始执行 ==========

    /**
     * BeforeAgentToolExecution的字段：
     * - agentInstance() → AgentInstance（有name()方法）
     * - toolExecution() → BeforeToolExecution（有request()方法）
     * - BeforeToolExecution.request() → ToolExecutionRequest（有name()和arguments()）
     */
    @Override
    public void beforeAgentToolExecution(BeforeAgentToolExecution before) {
        String agentName = before.agentInstance() != null ? before.agentInstance().name() : "unknown";
        ToolExecutionRequest request = before.toolExecution() != null ? before.toolExecution().request() : null;
        String toolName = request != null ? request.name() : "unknown";
        String args = request != null ? truncate(request.arguments()) : "";

        log.info("🔧 [TRACE] 工具开始: {} → {} | 参数: {}", agentName, toolName, args);
        traces.add(new TraceRecord(
                "tool_start", agentName + "/" + toolName, args,
                LocalDateTime.now(), -1, -1
        ));
    }

    // ========== Hook 7: 工具执行结束 ==========

    /**
     * AfterAgentToolExecution的字段：
     * - agentInstance() → AgentInstance
     * - toolExecution() → ToolExecution（有request()、result()、duration()）
     * - ToolExecution.request() → ToolExecutionRequest（有name()）
     * - ToolExecution.result() → String（工具返回结果）
     * - ToolExecution.duration() → Duration（从开始到结束的耗时）
     */
    @Override
    public void afterAgentToolExecution(AfterAgentToolExecution after) {
        String agentName = after.agentInstance() != null ? after.agentInstance().name() : "unknown";
        ToolExecution toolExec = after.toolExecution();
        ToolExecutionRequest request = toolExec != null ? toolExec.request() : null;
        String toolName = request != null ? request.name() : "unknown";
        String result = toolExec != null ? truncate(toolExec.result()) : "";
        long durationMs = toolExec != null && toolExec.duration() != null ? toolExec.duration().toMillis() : -1;

        log.info("🔩 [TRACE] 工具结束: {} → {} | 耗时:{}ms | 结果: {}", agentName, toolName, durationMs, result);
        traces.add(new TraceRecord(
                "tool_end", agentName + "/" + toolName, result,
                LocalDateTime.now(), durationMs, -1
        ));
    }

    // ========== 子Agent继承 ==========

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    // ========== 轨迹报告输出 ==========

    public String generateTraceReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=" .repeat(60)).append("\n");
        sb.append("📊 Agent执行轨迹报告\n");
        sb.append("=" .repeat(60)).append("\n\n");

        // 执行时间线
        sb.append("⏱️ 执行时间线:\n");
        sb.append("-".repeat(60)).append("\n");
        for (TraceRecord trace : traces) {
            String icon = switch (trace.type()) {
                case "agent_start" -> "🟡";
                case "agent_end" -> "🟢";
                case "agent_error" -> "🔴";
                case "tool_start" -> "🔧";
                case "tool_end" -> "🔩";
                case "scope_create" -> "🆔";
                case "scope_destroy" -> "🗑️";
                default -> "⚪";
            };
            String durationStr = trace.durationMs() >= 0 ? trace.durationMs() + "ms" : "-";
            String tokenStr = trace.tokenCount() >= 0 ? trace.tokenCount() + "tok" : "-";
            sb.append(String.format("  %s [%s] %s | 耗时:%s | Token:%s | %s\n",
                    icon, trace.timestamp().format(timeFmt), trace.agentName(),
                    durationStr, tokenStr, truncate(trace.content(), 80)));
        }
        sb.append("\n");

        // 统计汇总
        long totalDuration = traces.stream()
                .filter(t -> t.durationMs() >= 0)
                .mapToLong(TraceRecord::durationMs)
                .sum();
        int totalTokens = accumulatedTokens;
        int toolCallCount = (int) traces.stream().filter(t -> t.type().equals("tool_end")).count();
        int errorCount = (int) traces.stream().filter(t -> t.type().equals("agent_error")).count();
        int stepCount = traces.size();

        sb.append("📈 统计汇总:\n");
        sb.append("-".repeat(60)).append("\n");
        sb.append(String.format("  总步骤数: %d\n", stepCount));
        sb.append(String.format("  总耗时: %dms (%.1fs)\n", totalDuration, totalDuration / 1000.0));
        sb.append(String.format("  总Token: %d\n", totalTokens));
        sb.append(String.format("  工具调用: %d次\n", toolCallCount));
        sb.append(String.format("  错误次数: %d次\n", errorCount));
        sb.append("=" .repeat(60)).append("\n");

        return sb.toString();
    }

    // ========== 工具方法 ==========

    private String truncate(String s) {
        return truncate(s, 500);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...[truncated, total=" + s.length() + "]";
    }

    private String mapToString(java.util.Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        int count = 0;
        for (var entry : map.entrySet()) {
            if (count > 0) sb.append(", ");
            sb.append(entry.getKey()).append(":").append(truncate(String.valueOf(entry.getValue()), 100));
            count++;
        }
        sb.append("}");
        return sb.toString();
    }

    public List<TraceRecord> getTraces() {
        return traces;
    }
}
