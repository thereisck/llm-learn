package com.ck.custom.llmlearn.observability;

import com.ck.custom.llmlearn.agents.monitoring.TraceListener;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent调用链可视化器
 *
 * 【Week7 Day6 Step4】
 *
 * 核心职责：把TraceListener记录的线性轨迹，重构成树形调用链并可视化输出。
 *
 * 为什么需要树形结构？
 * - TraceListener记录的是扁平的event列表（agent_start/tool_start/tool_end/agent_end）
 * - 但Agent调用是有层级关系的：Agent → 子Agent → 工具 → 子工具
 * - 树形结构能清晰展示"谁调用了谁"，就像OpenTelemetry的Span树
 *
 * 对比OpenClaw的session transcript：
 * - OpenClaw: 每条消息记录sender/tool/cost，但展示是线性的
 * - 本可视化器: 重构成树形，用ASCII树状图展示调用层级
 * - 类比：OpenTelemetry的Trace树 vs 日志的线性输出
 *
 * 可视化效果示例：
 *
 *   🤖 CodeReviewer (1200ms | 500tok | ¥0.001)
 *   ├── 🔧 readFile("A.java") (50ms)
 *   ├── 🔧 readFile("B.java") (45ms)
 *   ├── 🤖 CodeAnalyzer (600ms | 300tok)
 *   │   ├── 🔧 analyzeStyle() (200ms)
 *   │   └── 🔧 analyzeSecurity() (350ms)
 *   └── 🤖 CodeSuggester (400ms | 200tok)
 *       └── 🔧 generateFix() (300ms)
 */
@Slf4j
public class AgentCallChainVisualizer {

    // ================================================================
    // 树节点数据模型
    // ================================================================

    /**
     * 调用链树节点
     *
     * 类比OpenTelemetry的Span：
     * - Span有parent/children关系 → 本节点也有parent/children
     * - Span有startTime/endTime → 本节点有durationMs
     * - Span有attributes → 本节点有metadata
     *
     * 节点类型：
     * - AGENT: Agent调用（可能包含子Agent和工具调用）
     * - TOOL: 工具调用（叶子节点，不再有子节点）
     * - ROOT: 虚拟根节点（用于挂载多个顶层Agent调用）
     */
    public static class CallTreeNode {
        public enum NodeType { ROOT, AGENT, TOOL }

        private final NodeType type;
        private final String name;           // Agent名 或 工具名
        private long durationMs;             // 耗时（毫秒）
        private int tokenCount;              // Token数（Agent节点有，Tool节点为0）
        private long costMicroYuan;          // 成本（微元）
        private final String detail;         // 附加信息（输入参数/输出摘要）
        private final List<CallTreeNode> children = new ArrayList<>();
        private final CallTreeNode parent;

        public CallTreeNode(NodeType type, String name, CallTreeNode parent, String detail) {
            this.type = type;
            this.name = name;
            this.parent = parent;
            this.detail = detail;
            this.durationMs = -1;
            this.tokenCount = -1;
            this.costMicroYuan = 0;
        }

        public CallTreeNode addChild(NodeType type, String name, String detail) {
            CallTreeNode child = new CallTreeNode(type, name, this, detail);
            children.add(child);
            return child;
        }

        // Getters
        public NodeType getType() { return type; }
        public String getName() { return name; }
        public long getDurationMs() { return durationMs; }
        public int getTokenCount() { return tokenCount; }
        public long getCostMicroYuan() { return costMicroYuan; }
        public String getDetail() { return detail; }
        public List<CallTreeNode> getChildren() { return children; }
        public CallTreeNode getParent() { return parent; }

        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public void setTokenCount(int tokenCount) { this.tokenCount = tokenCount; }
        public void setCostMicroYuan(long costMicroYuan) { this.costMicroYuan = costMicroYuan; }

        /**
         * 递归计算总Token（自身 + 所有子节点）
         */
        public int getTotalTokensRecursive() {
            int self = tokenCount > 0 ? tokenCount : 0;
            int childrenTotal = children.stream()
                    .mapToInt(CallTreeNode::getTotalTokensRecursive)
                    .sum();
            return self + childrenTotal;
        }

        /**
         * 递归计算总成本（自身 + 所有子节点）
         */
        public long getTotalCostRecursive() {
            long self = costMicroYuan;
            long childrenTotal = children.stream()
                    .mapToLong(CallTreeNode::getTotalCostRecursive)
                    .sum();
            return self + childrenTotal;
        }

        /**
         * 递归计算总耗时（自身 + 所有子节点）
         */
        public long getTotalDurationRecursive() {
            long self = durationMs > 0 ? durationMs : 0;
            long childrenTotal = children.stream()
                    .mapToLong(CallTreeNode::getTotalDurationRecursive)
                    .sum();
            return self + childrenTotal;
        }
    }

    // ================================================================
    // 从TraceListener轨迹构建树
    // ================================================================

    /**
     * 从TraceListener的扁平轨迹列表构建调用树
     *
     * 算法：用栈模拟嵌套关系
     * - 遇到 agent_start → 创建AGENT节点，压栈
     * - 遇到 tool_start → 创建TOOL节点，挂到栈顶Agent的子节点
     * - 遇到 tool_end → 找到对应的TOOL节点，填充duration
     * - 遇到 agent_end → 弹栈，填充duration/token
     *
     * 对比OpenTelemetry的Span构建：
     * OpenTelemetry用Context传递parent Span，自动构建树
     * 我们用栈手动构建，因为TraceListener没有Context传递机制
     *
     * @param traces TraceListener记录的轨迹列表
     *               （复用Week6的TraceListener.TraceRecord）
     * @return 根节点
     */
    public CallTreeNode buildFromTraces(List<dev.langchain4j.agentic.observability.AgentListener> listeners) {
        // 这个方法需要直接用TraceListener的TraceRecord
        // 但TraceRecord是TraceListener的内部record，我们换个思路
        // 直接在Demo里用buildFromTraceRecords
        throw new UnsupportedOperationException("Use buildFromTraceRecords instead");
    }

    /**
     * 从TraceRecord列表构建调用树
     *
     * @param traceRecords TraceListener.getTraces() 返回的列表
     * @return 根节点（ROOT类型，子节点是顶层Agent调用）
     */
    public CallTreeNode buildFromTraceRecords(List<TraceListener.TraceRecord> traceRecords) {
        CallTreeNode root = new CallTreeNode(CallTreeNode.NodeType.ROOT, "root", null, "");

        // 栈：模拟嵌套调用
        // 栈顶是当前正在执行的Agent节点
        java.util.Deque<CallTreeNode> agentStack = new java.util.ArrayDeque<>();

        // 工具节点临时映射：tool_start时创建，tool_end时填充duration
        // key = "agentName/toolName"，value = 最近创建的未完成tool节点
        Map<String, CallTreeNode> pendingTools = new HashMap<>();

        for (TraceListener.TraceRecord trace : traceRecords) {
            switch (trace.type()) {
                case "agent_start" -> {
                    CallTreeNode agentNode = new CallTreeNode(
                            CallTreeNode.NodeType.AGENT,
                            trace.agentName(),
                            agentStack.isEmpty() ? root : agentStack.peek(),
                            trace.content()
                    );
                    if (!agentStack.isEmpty()) {
                        agentStack.peek().addChild(CallTreeNode.NodeType.AGENT, trace.agentName(), trace.content());
                        // 实际上应该用上面创建的agentNode，但addChild返回的是新节点
                        // 修正：用addChild返回的节点压栈
                        agentStack.push(agentStack.peek().getChildren().get(agentStack.peek().getChildren().size() - 1));
                    } else {
                        root.getChildren().add(agentNode);
                        agentStack.push(agentNode);
                    }
                }

                case "agent_end" -> {
                    if (!agentStack.isEmpty()) {
                        CallTreeNode finishedAgent = agentStack.pop();
                        finishedAgent.setDurationMs(trace.durationMs());
                        finishedAgent.setTokenCount(trace.tokenCount());
                    }
                }

                case "agent_error" -> {
                    if (!agentStack.isEmpty()) {
                        CallTreeNode errorAgent = agentStack.pop();
                        errorAgent.setDurationMs(trace.durationMs());
                        errorAgent.setTokenCount(trace.tokenCount());
                        // 标记错误
                        // detail字段已经记录了错误信息
                    }
                }

                case "tool_start" -> {
                    if (!agentStack.isEmpty()) {
                        CallTreeNode toolNode = agentStack.peek().addChild(
                                CallTreeNode.NodeType.TOOL,
                                trace.agentName(),  // 格式是 "agentName/toolName"
                                trace.content()
                        );
                        String key = trace.agentName() + "|" + trace.timestamp();
                        pendingTools.put(key, toolNode);
                    }
                }

                case "tool_end" -> {
                    // 找到对应的pending tool节点并填充duration
                    // 因为tool_start和tool_end的agentName相同（格式 "agent/tool"）
                    // 找最后一个未完成的同名tool节点
                    String toolKey = trace.agentName();
                    CallTreeNode foundTool = null;
                    String foundKey = null;
                    for (Map.Entry<String, CallTreeNode> entry : pendingTools.entrySet()) {
                        if (entry.getKey().startsWith(toolKey + "|")) {
                            foundTool = entry.getValue();
                            foundKey = entry.getKey();
                            break;  // 取第一个匹配的
                        }
                    }
                    if (foundTool != null && foundKey != null) {
                        foundTool.setDurationMs(trace.durationMs());
                        pendingTools.remove(foundKey);
                    }
                }

                // scope_create 和 scope_destroy 不影响树结构，跳过
                default -> {}
            }
        }

        return root;
    }

    // ================================================================
    // 树形可视化输出
    // ================================================================

    /**
     * 渲染调用树为ASCII树状图
     *
     * 效果：
     *   🤖 CodeReviewer (1200ms | 500tok | ¥0.001)
     *   ├── 🔧 readFile("A.java") (50ms)
     *   ├── 🔧 readFile("B.java") (45ms)
     *   ├── 🤖 CodeAnalyzer (600ms | 300tok)
     *   │   ├── 🔧 analyzeStyle() (200ms)
     *   │   └── 🔧 analyzeSecurity() (350ms)
     *   └── 🤖 CodeSuggester (400ms | 200tok)
     *       └── 🔧 generateFix() (300ms)
     *
     * @param root 调用树根节点
     * @return ASCII树状图字符串
     */
    public String renderTree(CallTreeNode root) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("🌳 Agent调用链可视化\n");
        sb.append("─".repeat(70)).append("\n\n");
        renderNode(sb, root, "", true, true);
        sb.append("\n");

        // 汇总信息
        renderSummary(sb, root);

        return sb.toString();
    }

    /**
     * 递归渲染单个节点
     *
     * @param sb        StringBuilder
     * @param node      当前节点
     * @param prefix    前缀（用于控制缩进和连接线）
     * @param isLast    是否是父节点的最后一个子节点
     * @param isRoot    是否是根节点
     */
    private void renderNode(StringBuilder sb, CallTreeNode node, String prefix, boolean isLast, boolean isRoot) {
        if (!isRoot) {
            // 绘制连接线
            sb.append(prefix);
            sb.append(isLast ? "└── " : "├── ");
        }

        // 节点内容
        if (node.getType() == CallTreeNode.NodeType.ROOT) {
            // 根节点不绘制自身，只渲染子节点
            for (int i = 0; i < node.getChildren().size(); i++) {
                renderNode(sb, node.getChildren().get(i), prefix,
                        i == node.getChildren().size() - 1, false);
            }
            return;
        }

        // 图标
        String icon = switch (node.getType()) {
            case AGENT -> "🤖";
            case TOOL -> "🔧";
            case ROOT -> "📍";
        };

        // 节点信息
        sb.append(icon).append(" ").append(node.getName());

        // 附加信息（耗时/Token/成本）
        List<String> infos = new ArrayList<>();
        if (node.getDurationMs() >= 0) {
            infos.add(formatDuration(node.getDurationMs()));
        }
        if (node.getTokenCount() >= 0) {
            infos.add(node.getTokenCount() + "tok");
        }
        if (node.getCostMicroYuan() > 0) {
            infos.add(formatCost(node.getCostMicroYuan()));
        }
        if (!infos.isEmpty()) {
            sb.append(" (").append(String.join(" | ", infos)).append(")");
        }

        // 详情（截断）
        if (node.getDetail() != null && !node.getDetail().isEmpty()
                && !node.getDetail().equals("null") && !node.getDetail().equals("{}")) {
            String detail = truncate(node.getDetail(), 60);
            sb.append("\n").append(prefix);
            sb.append(isLast ? "    " : "│   ");
            sb.append("💡 ").append(detail);
        }

        sb.append("\n");

        // 递归渲染子节点
        String childPrefix = prefix + (isLast ? "    " : "│   ");
        for (int i = 0; i < node.getChildren().size(); i++) {
            renderNode(sb, node.getChildren().get(i), childPrefix,
                    i == node.getChildren().size() - 1, false);
        }
    }

    // ================================================================
    // 汇总信息
    // ================================================================

    private void renderSummary(StringBuilder sb, CallTreeNode root) {
        sb.append("📊 调用链汇总:\n");
        sb.append("─".repeat(70)).append("\n");

        int agentCount = countNodes(root, CallTreeNode.NodeType.AGENT);
        int toolCount = countNodes(root, CallTreeNode.NodeType.TOOL);
        long totalDuration = root.getTotalDurationRecursive();
        int totalTokens = root.getTotalTokensRecursive();
        long totalCost = root.getTotalCostRecursive();
        int maxDepth = calculateDepth(root);

        sb.append(String.format("  Agent调用: %d次\n", agentCount));
        sb.append(String.format("  工具调用: %d次\n", toolCount));
        sb.append(String.format("  最大嵌套深度: %d层\n", maxDepth));
        sb.append(String.format("  总耗时: %s\n", formatDuration(totalDuration)));
        sb.append(String.format("  总Token: %d\n", totalTokens));
        sb.append(String.format("  总成本: %s\n", formatCost(totalCost)));

        // 瓶颈分析：找出最耗时的节点
        CallTreeNode bottleneck = findBottleneck(root);
        if (bottleneck != null) {
            sb.append(String.format("\n  ⚡ 瓶颈节点: %s → %s\n",
                    bottleneck.getName(), formatDuration(bottleneck.getDurationMs())));
        }

        // 对比OpenClaw
        sb.append("\n");
        sb.append("🔗 对比OpenClaw:\n");
        sb.append("─".repeat(70)).append("\n");
        sb.append("  OpenClaw: session transcript线性记录每条消息\n");
        sb.append("  本可视化器: 重构成树形调用链，展示层级关系\n");
        sb.append("  OpenClaw用session+subagent管理嵌套 → 我们用栈重建嵌套\n");
        sb.append("  类比: OpenTelemetry Span树 vs 日志线性输出\n");
        sb.append("─".repeat(70)).append("\n");
    }

    // ================================================================
    // 工具方法
    // ================================================================

    private int countNodes(CallTreeNode node, CallTreeNode.NodeType type) {
        int count = node.getType() == type ? 1 : 0;
        for (CallTreeNode child : node.getChildren()) {
            count += countNodes(child, type);
        }
        return count;
    }

    private int calculateDepth(CallTreeNode node) {
        if (node.getChildren().isEmpty()) return 0;
        int maxChildDepth = 0;
        for (CallTreeNode child : node.getChildren()) {
            maxChildDepth = Math.max(maxChildDepth, calculateDepth(child));
        }
        return maxChildDepth + 1;
    }

    private CallTreeNode findBottleneck(CallTreeNode node) {
        CallTreeNode max = node;
        for (CallTreeNode child : node.getChildren()) {
            CallTreeNode childMax = findBottleneck(child);
            if (childMax != null && childMax.getDurationMs() > max.getDurationMs()) {
                max = childMax;
            }
        }
        return max.getType() == CallTreeNode.NodeType.ROOT ? null : max;
    }

    private String formatDuration(long ms) {
        if (ms < 0) return "?";
        if (ms < 1000) return ms + "ms";
        return String.format("%.1fs", ms / 1000.0);
    }

    private String formatCost(long microYuan) {
        if (microYuan == 0) return "¥0";
        double yuan = microYuan / 1_000_000.0;
        if (yuan < 0.01) return String.format("¥%.4f", yuan);
        return String.format("¥%.2f", yuan);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
