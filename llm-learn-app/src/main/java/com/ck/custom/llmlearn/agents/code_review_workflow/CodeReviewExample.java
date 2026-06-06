package com.ck.custom.llmlearn.agents.code_review_workflow;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
/**
 * @author changkong
 * @date 2026/5/31 18:20
 **/
public class CodeReviewExample {



    public static void main(String[] args) {
        // 1. 创建 ChatModel
        ChatModel CHAT_MODEL = getModel();
        // 2. 创建3个子 Agent，每个配好 outputKey
        CodeReviewer codeReviewer = AgenticServices
                .agentBuilder(CodeReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("codeReview")
                .build();

        SecurityScanner securityScanner = AgenticServices
                .agentBuilder(SecurityScanner.class)
                .chatModel(CHAT_MODEL)
                .outputKey("securityReview")
                .build();

        ReportGenerator reportGenerator = AgenticServices
                .agentBuilder(ReportGenerator.class)
                .chatModel(CHAT_MODEL)
                .outputKey("finalReport")
                .build();
        // 3. 用 sequenceBuilder 组合成 CodeReviewWorkflow
        UntypedAgent workflow = AgenticServices
                .sequenceBuilder()
                .subAgents(codeReviewer, securityScanner, reportGenerator)
                .outputKey("finalReport")
                .build();
        // 4. 准备一段有问题的 Java 代码作为 codeSnippet
        String codeSnippet = """
                public class UserService
                {
                    public void createUser(String username, String password) {
                        String sql = "INSERT INTO users (username, password) VALUES ('" + username + "', '" + password + "')";
                        // 执行 SQL 语句
                    }
                }
                """;
        // 5. 调用 workflow（UntypedAgent需要传Map）
        java.util.Map<String, Object> arguments = java.util.Map.of("codeSnippet", codeSnippet);
        String result = (String) workflow.invoke(arguments);
        // 6. 打印结果
        System.out.println("最终代码审核报告：\n" + result);
    }

    public static ChatModel getModel() {
        String byaiKey = System.getenv("BYAI_API_KEY");
        if (byaiKey == null || byaiKey.isEmpty()) {
            byaiKey = ""; // fallback
        }
        return OpenAiChatModel.builder()
                .baseUrl("https://model.indata.cc/v1")
                .apiKey(byaiKey)
                .modelName("glm-5")
                .timeout(java.time.Duration.ofSeconds(120))  // 中转站响应慢，加大超时
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
