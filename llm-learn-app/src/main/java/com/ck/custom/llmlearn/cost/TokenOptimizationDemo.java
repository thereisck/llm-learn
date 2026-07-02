package com.ck.custom.llmlearn.cost;

/**
 * @author changkong
 * @date 2026/6/21 14:58
 **/

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Week7 Day4 - Step4: Token 精简 — 少花钱多办事
 *
 * 三个优化手段：
 * 1. Prompt瘦身：去掉冗余指令，合并重复约束
 * 2. 历史消息裁剪：只保留最近N轮 + 系统提示
 * 3. 输出长度控制：用 maxTokens 限制输出，别让模型废话
 *
 * 核心公式：省Token = 省钱 = 省时间
 * GPT-4o: 输入$2.5/M tokens, 输出$10/M tokens
 * 一次调用省500 tokens → 1万次调用省$5 → 10万次省$50
 *
 * 运行方式：直接跑main方法
 * 观察重点：
 * 1. 冗长Prompt vs 精简Prompt 的Token数和效果对比
 * 2. 历史消息裁剪前后的Token数差异
 * 3. maxTokens限制如何避免模型输出过长
 *
 * @author changkong
 * @date 2026/6/21
 */
public class TokenOptimizationDemo {
    // ========== API 配置 ==========
    private static final String BASE_URL = "https://api.siliconflow.cn/v1";
    private static final String API_KEY = "sk-kemgxaacaqdehipgoywavavmpaiazkocfjyixcvsnslmeycv";
    private static final String MODEL_NAME = "Pro/zai-org/GLM-5.1";
    private static ChatModel createModel() {
        return OpenAiChatModel.builder()
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    // ========== Token 估算工具 ==========
    /**
     * 粗略估算Token数（中英文混合）
     * 经验值：英文 ~4字符/token，中文 ~1.5字/token
     * 不精确但够用，生产环境用 tiktoken 精确计算
     */
    static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int chineseCount = 0;
        int englishCharCount = 0;
        for (char c : text.toCharArray()) {
            if (String.valueOf(c).getBytes().length > 1) {
                chineseCount++;
            } else if (Character.isLetterOrDigit(c) || c == ' ') {
                englishCharCount++;
            }
        }
        // 中文 ~1.5字/token，英文 ~4字符/token
        return (int) Math.ceil(chineseCount / 1.5 + englishCharCount / 4.0);
    }

    // ========== 优化1：Prompt 瘦身 ==========
    /**
     * 冗长版 Prompt：啰嗦、重复、没重点
     */
    static String verbosePrompt(String code) {
        return """
                你是一个专业的Java代码审查专家，拥有20年的Java开发经验。
                请你仔细审查以下Java代码，从多个角度进行分析：
                首先，请检查代码是否有语法错误。
                其次，请检查代码是否有逻辑错误。
                然后，请检查代码是否有安全问题。
                接着，请检查代码是否有性能问题。
                最后，请检查代码是否符合Java编码规范。
                请给出详细的审查报告，包括问题描述、严重程度和修改建议。
                每个问题请给出具体的代码示例。
                请用中文回答。
                代码如下：
                """ + code;
    }
    /**
     * 精简版 Prompt：直接、结构化、不废话
     * 同样的任务，Token 少一半
     */
    static String leanPrompt(String code) {
        return """
                审查以下Java代码，按格式输出：
                [类型] 问题描述 → 修改建议
                类型：语法/逻辑/安全/性能/规范
                """ + code;
    }

    // ========== 优化2：历史消息裁剪 ==========
    /**
     * 模拟多轮对话历史
     */
    static List<String> simulateMultiTurnHistory() {
        List<String> history = new ArrayList<>();
        history.add("System: 你是一个Java技术助手。");
        history.add("User: 什么是Spring Boot的自动配置？");
        history.add("AI: Spring Boot自动配置通过@EnableAutoConfiguration...");
        history.add("User: 自动配置的优先级怎么控制？");
        history.add("AI: 通过@Order注解或AutoConfigureBefore/After...");
        history.add("User: 如何排除某个自动配置？");
        history.add("AI: 使用@SpringBootApplication的exclude属性...");
        history.add("User: @ConditionalOnClass的作用是什么？");
        history.add("AI: 当指定的类在classpath中存在时，配置才生效...");
        history.add("User: 说说你对RAG的理解");  // 这是真正要问的
        return history;
    }
    /**
     * 全量历史 → Token多
     */
    static String buildFullHistoryPrompt(List<String> history) {
        return String.join("\n", history) + "\n\nAI:";
    }
    /**
     * 裁剪历史 → 只保留 System + 最近2轮 + 当前问题
     */
    static String buildTrimmedHistoryPrompt(List<String> history) {
        // 保留 System（第1条）+ 最近4条（2轮对话）+ 当前问题
        String systemMsg = history.get(0);
        int size = history.size();
        // 当前问题在最后1条
        String currentQuestion = history.get(size - 1);
        // 最近2轮对话（倒数第5到倒数第2，共4条）
        int trimStart = Math.max(1, size - 5);
        List<String> recentMessages = history.subList(trimStart, size - 1);
        StringBuilder sb = new StringBuilder();
        sb.append(systemMsg).append("\n");
        sb.append("...(更早的对话已省略)...\n");
        for (String msg : recentMessages) {
            sb.append(msg).append("\n");
        }
        sb.append(currentQuestion).append("\n\nAI:");
        return sb.toString();
    }
    // ========== 测试场景 ==========
    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Week7 Day4 Step4: Token 精简 — 少花钱多办事");
        System.out.println("=".repeat(60));
        if (API_KEY.isEmpty()) {
            System.err.println("❌ 请先设置环境变量 SILICONFLOW_API_KEY");
            return;
        }
        ChatModel model = createModel();
        // ========== 优化1：Prompt 瘦身对比 ==========
        System.out.println("\n【优化1】Prompt 瘦身对比");
        System.out.println("-".repeat(50));
        String code = "public List<User> getUsers() { List<User> users = new ArrayList<>(); for (User u : userDao.findAll()) { if (u.getAge() > 18) { users.add(u); } } return users; }";
        String verbose = verbosePrompt(code);
        String lean = leanPrompt(code);
        int verboseTokens = estimateTokens(verbose);
        int leanTokens = estimateTokens(lean);
        System.out.printf("冗长版 Prompt: %d 字符 ≈ %d tokens%n", verbose.length(), verboseTokens);
        System.out.printf("精简版 Prompt: %d 字符 ≈ %d tokens%n", lean.length(), leanTokens);
        System.out.printf("节省: %d tokens (%.1f%%)%n%n",
                verboseTokens - leanTokens,
                (verboseTokens - leanTokens) * 100.0 / verboseTokens);
        // 实际调用对比效果
        System.out.println("--- 冗长版调用 ---");
        long start1 = System.currentTimeMillis();
        String response1 = model.chat(verbose);
        long latency1 = System.currentTimeMillis() - start1;
        System.out.printf("响应(%d字符, %dms): %s%n%n",
                response1.length(), latency1, truncate(response1, 200));
        System.out.println("--- 精简版调用 ---");
        long start2 = System.currentTimeMillis();
        String response2 = model.chat(lean);
        long latency2 = System.currentTimeMillis() - start2;
        System.out.printf("响应(%d字符, %dms): %s%n%n",
                response2.length(), latency2, truncate(response2, 200));
        System.out.println("💡 对比：精简版响应时间通常更快（输入Token少→处理快）");
        // ========== 优化2：历史消息裁剪 ==========
        System.out.println("\n【优化2】历史消息裁剪对比");
        System.out.println("-".repeat(50));
        List<String> history = simulateMultiTurnHistory();
        String fullHistory = buildFullHistoryPrompt(history);
        String trimmedHistory = buildTrimmedHistoryPrompt(history);
        int fullTokens = estimateTokens(fullHistory);
        int trimmedTokens = estimateTokens(trimmedHistory);
        System.out.printf("全量历史(%d条消息): %d 字符 ≈ %d tokens%n",
                history.size(), fullHistory.length(), fullTokens);
        System.out.printf("裁剪后(System+最近2轮): %d 字符 ≈ %d tokens%n",
                trimmedHistory.length(), trimmedTokens);
        System.out.printf("节省: %d tokens (%.1f%%)%n",
                fullTokens - trimmedTokens,
                (fullTokens - trimmedTokens) * 100.0 / fullTokens);
        System.out.println("\n--- 全量历史调用 ---");
        long start3 = System.currentTimeMillis();
        String response3 = model.chat(fullHistory);
        long latency3 = System.currentTimeMillis() - start3;
        System.out.printf("响应(%dms): %s%n%n", latency3, truncate(response3, 200));
        System.out.println("--- 裁剪历史调用 ---");
        long start4 = System.currentTimeMillis();
        String response4 = model.chat(trimmedHistory);
        long latency4 = System.currentTimeMillis() - start4;
        System.out.printf("响应(%dms): %s%n", latency4, truncate(response4, 200));
        // ========== 优化3：输出长度控制 ==========
        System.out.println("\n【优化3】输出长度控制（maxTokens）");
        System.out.println("-".repeat(50));
        // LangChain4j OpenAiChatModel 支持 maxTokens 配置
        ChatModel limitedModel = OpenAiChatModel.builder()
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .maxTokens(50)  // 只允许50个Token输出
                .timeout(Duration.ofSeconds(60))
                .build();
        String longPrompt = "详细介绍Java的垃圾回收机制，包括：分代模型、GC算法、收集器类型、调优参数。";
        System.out.println("问题: " + longPrompt);
        System.out.println("限制: maxTokens=50（约30-40个中文字）");
        long start5 = System.currentTimeMillis();
        String shortResponse = limitedModel.chat(longPrompt);
        long latency5 = System.currentTimeMillis() - start5;
        System.out.printf("响应(%d字符, %dms): %s%n",
                shortResponse.length(), latency5, shortResponse);
        System.out.println("💡 观察：输出被截断在约50 tokens处，避免废话，省输出Token费用");
        // ========== 总结对比 ==========
        System.out.println("\n" + "=".repeat(60));
        System.out.println("Step4 完成！核心收获：");
        System.out.println("1. Prompt瘦身：去掉客套话和重复指令，Token省30-50%");
        System.out.println("2. 历史裁剪：只保留System + 最近2轮，多轮对话Token线性增长→恒定");
        System.out.println("3. maxTokens控制：限制输出长度，防止模型废话烧输出Token");
        System.out.println("4. 省Token公式：精简输入 + 裁剪历史 + 限制输出 = 三省齐下");
        System.out.println("5. 注意平衡：太精简可能丢失上下文导致答非所问");
        System.out.println("=".repeat(60));
    }
    private static String truncate(String text, int maxLen) {
        if (text == null) return "[null]";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
