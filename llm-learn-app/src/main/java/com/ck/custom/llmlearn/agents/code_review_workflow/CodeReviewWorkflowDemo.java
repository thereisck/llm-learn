package com.ck.custom.llmlearn.agents.code_review_workflow;

/**
 * @author changkong
 * @date 2026/6/14 17:58
 **/

import com.ck.custom.llmlearn.agents.monitoring.TraceListener;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;


/**
 * Week6 Day7 实战：多Agent代码审查团队
 *
 * 流水线架构（修复版）：
 *   CodeReader → CodeAnalyzer → 条件分支
 *     ├── 深度路径（issueCount > 3）：Suggester↔Validator循环（最多3次）
 *     └── 快速路径（issueCount <= 3）：Suggester → Validator（一次过）
 *
 * ⚠️ 关键修复：
 * 1. Agent接口只声明初始输入的@V参数（codeSnippet）
 *    中间结果（codeStructure/issues/suggestions）通过AgenticScope自动流转
 * 2. 条件分支嵌套完整的子workflow，不是选单个Agent
 *    深度路径 = loopBuilder(Suggester→Validator循环)
 *    快速路径 = sequenceBuilder(Suggester→Validator顺序)
 *
 * 监控：TraceListener追踪整条流水线执行轨迹
 */
@Slf4j
public class CodeReviewWorkflowDemo {

    public static void main(String[] args) {
        log.info("==================================================");
        log.info("Week6 Day7: 多Agent代码审查团队 Demo");
        log.info("==================================================");

        // ========== 1. 创建ChatModel + 监控 ==========

        ChatModel chatModel = createChatModel();
        TraceListener traceListener = new TraceListener();

        // ========== 2. 创建4个子Agent ==========

        // Agent 1: CodeReader - 读取代码，提取结构
        CodeReader codeReader = AgenticServices
                .agentBuilder(CodeReader.class)
                .chatModel(chatModel)
                .outputKey("codeStructure")
                .listener(traceListener)
                .build();

        // Agent 2: CodeAnalyzer - 分析问题，输出issueCount
        CodeAnalyzer codeAnalyzer = AgenticServices
                .agentBuilder(CodeAnalyzer.class)
                .chatModel(chatModel)
                .outputKey("issues")
                .listener(traceListener)
                .build();

        // Agent 3: CodeSuggester（深度路径用） - 生成改进建议
        CodeSuggester deepSuggester = AgenticServices
                .agentBuilder(CodeSuggester.class)
                .chatModel(chatModel)
                .outputKey("suggestions")
                .listener(traceListener)
                .build();

        // Agent 3b: CodeSuggester（快速路径用） - 生成改进建议
        CodeSuggester quickSuggester = AgenticServices
                .agentBuilder(CodeSuggester.class)
                .chatModel(chatModel)
                .outputKey("suggestions")
                .listener(traceListener)
                .build();

        // Agent 4: CodeValidator（深度路径用） - 验证建议可行性
        CodeValidator deepValidator = AgenticServices
                .agentBuilder(CodeValidator.class)
                .chatModel(chatModel)
                .outputKey("finalReport")
                .listener(traceListener)
                .build();

        // Agent 4b: CodeValidator（快速路径用） - 验证建议可行性
        CodeValidator quickValidator = AgenticServices
                .agentBuilder(CodeValidator.class)
                .chatModel(chatModel)
                .outputKey("finalReport")
                .listener(traceListener)
                .build();

        // ========== 3. 构建两条子workflow ==========

        // 深度审查路径：Suggester↔Validator循环，最多3次迭代
        UntypedAgent deepReviewPath = AgenticServices
                .loopBuilder()
                .subAgents(deepSuggester, deepValidator)
                .outputKey("finalReport")
                .exitCondition(agenticScope -> {
                    String finalReport = (String) agenticScope.readState("finalReport");
                    if (finalReport == null) {
                        log.info("🔄 循环检查：finalReport还没生成，继续迭代");
                        return false;
                    }
                    boolean accepted = finalReport.toLowerCase().contains("\"accepted\": true")
                            || finalReport.toLowerCase().contains("\"accepted\":true")
                            || finalReport.toLowerCase().contains("accepted")
                            || finalReport.toLowerCase().contains("通过");
                    log.info("🔄 循环检查：Validator判定 {} → {}",
                            accepted ? "✅ 通过" : "❌ 不通过",
                            accepted ? "退出循环" : "继续迭代");
                    return accepted;
                })
                .maxIterations(3)
                .build();

        // 快速审查路径：Suggester→Validator顺序执行，一次过
        UntypedAgent quickReviewPath = AgenticServices
                .sequenceBuilder()
                .subAgents(quickSuggester, quickValidator)
                .outputKey("finalReport")
                .build();

        // ========== 4. 条件分支：选深度/快速 ==========

        UntypedAgent conditionalReview = AgenticServices
                .conditionalBuilder()
                .subAgents(
                        agenticScope -> extractIssueCount(agenticScope) > 3,
                        deepReviewPath     // 深度路径：循环迭代
                )
                .subAgents(
                        agenticScope -> extractIssueCount(agenticScope) <= 3,
                        quickReviewPath    // 快速路径：一次过
                )
                .build();

        // ========== 5. 组合完整流水线 ==========

        // 流水线：Reader → Analyzer → 条件分支(选深度/快速路径)
        UntypedAgent fullPipeline = AgenticServices
                .sequenceBuilder()
                .subAgents(
                        codeReader,           // Step1: 读取代码结构
                        codeAnalyzer,         // Step2: 分析问题
                        conditionalReview     // Step3: 条件分支选完整子workflow
                )
                .outputKey("finalReport")
                .build();

        // ========== 6. 准备测试代码 ==========

        // 一段有明显问题的Java代码（SQL注入 + 命名差 + 无异常处理 + 硬编码密码）
        String badCode = """
                public class UserService {
                    
                    private String DB_URL = "jdbc:mysql://localhost:3306/mydb";
                    private String DB_USER = "admin";
                    private String DB_PASS = "hardcoded_password_123";
                    
                    public void createUser(String username, String password) {
                        String sql = "INSERT INTO users (username, password) VALUES ('" + username + "', '" + password + "')";
                        executeSql(sql);
                    }
                    
                    public User getUser(String id) {
                        String sql = "SELECT * FROM users WHERE id = '" + id + "'";
                        return executeQuery(sql);
                    }
                    
                    public void deleteUser(String id) {
                        String sql = "DELETE FROM users WHERE id = '" + id + "'";
                        executeSql(sql);
                    }
                    
                    private void executeSql(String sql) {
                        // TODO: 实现数据库执行逻辑
                    }
                    
                    private User executeQuery(String sql) {
                        // TODO: 实现查询逻辑
                        return null;
                    }
                }
                """;

        // 一段相对干净的Java代码（只有轻微命名问题，预期走快速路径）
        String cleanCode = """
                public class OrderService {
                    
                    private final OrderRepository orderRepo;
                    
                    public OrderService(OrderRepository orderRepo) {
                        this.orderRepo = orderRepo;
                    }
                    
                    public Order findOrder(String orderId) {
                        return orderRepo.findById(orderId);
                    }
                    
                    public Order createOrder(OrderRequest request) {
                        Order order = new Order(request.getItems(), request.getCustomerId());
                        return orderRepo.save(order);
                    }
                }
                """;

        // ========== 7. 执行测试 ==========

        log.info("\n--- 测试1: 问题代码（预期走深度审查路径） ---");
        Map<String, Object> args1 = Map.of("codeSnippet", badCode);
        String result1 = (String) fullPipeline.invoke(args1);
        log.info("📋 最终审查报告:\n{}", result1);

        // 打印监控轨迹
        log.info("\n{}", traceListener.generateTraceReport());

        // ========== 8. 测试2: 干净代码（预期走快速审查路径） ==========

        log.info("\n--- 测试2: 干净代码（预期走快速审查路径） ---");
        traceListener.getTraces().clear();
        Map<String, Object> args2 = Map.of("codeSnippet", cleanCode);
        String result2 = (String) fullPipeline.invoke(args2);
        log.info("📋 最终审查报告:\n{}", result2);

        // 打印监控轨迹对比
        log.info("\n{}", traceListener.generateTraceReport());

        // ========== 9. 汇总 ==========

        log.info("\n==================================================");
        log.info("🎉 Week6 Day7 Demo完成！多Agent代码审查团队跑通！");
        log.info("流水线: Reader→Analyzer→条件分支(深度循环/快速顺序)");
        log.info("==================================================");
    }

    // ========== 工具方法 ==========

    /**
     * 从AgenticScope中提取issueCount
     * Analyzer输出的JSON里包含issueCount字段
     */
    private static int extractIssueCount(AgenticScope scope) {
        String issues = (String) scope.readState("issues");
        if (issues == null) {
            log.warn("⚠️ scope中没有issues数据，默认走快速审查路径");
            return 0;
        }
        try {
            String lower = issues.toLowerCase();
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "\"issuecount\"\\s*:\\s*(\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(lower);
            if (matcher.find()) {
                int count = Integer.parseInt(matcher.group(1));
                log.info("📊 从Analyzer输出提取issueCount = {}", count);
                return count;
            }
            // fallback: 用问题列表长度估算
            java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\\d+");
            java.util.regex.Matcher idMatcher = idPattern.matcher(issues);
            int estimatedCount = 0;
            while (idMatcher.find()) estimatedCount++;
            if (estimatedCount > 0) {
                log.info("📊 用问题列表估算issueCount ≈ {}", estimatedCount);
                return estimatedCount;
            }
            // fallback2: 看overallScore
            java.util.regex.Pattern scorePattern = java.util.regex.Pattern.compile(
                    "\"overallscore\"\\s*:\\s*(\\d+\\.?\\d*)");
            java.util.regex.Matcher scoreMatcher = scorePattern.matcher(lower);
            if (scoreMatcher.find()) {
                double score = Double.parseDouble(scoreMatcher.group(1));
                log.info("📊 用overallScore={}估算", score);
                return score < 5 ? 10 : 1;
            }
        } catch (Exception e) {
            log.warn("⚠️ 解析issueCount失败: {}", e.getMessage());
        }
        log.info("📊 无法提取issueCount，默认走快速审查路径");
        return 0;
    }

    private static ChatModel createChatModel() {
        String apiKey = System.getenv("SILICONFLOW_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) apiKey = "";

        return OpenAiChatModel.builder()
                .baseUrl("https://api.siliconflow.cn/v1")
                .apiKey(apiKey)
                .modelName("Pro/zai-org/GLM-5.1")
                .timeout(Duration.ofSeconds(120))
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
