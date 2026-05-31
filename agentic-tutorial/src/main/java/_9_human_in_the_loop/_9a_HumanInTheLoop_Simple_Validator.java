package _9_human_in_the_loop;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.model.chat.ChatModel;
import domain.CvReview;
import util.ChatModelProvider;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Scanner;

public class _9a_HumanInTheLoop_Simple_Validator {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 300);
    }

    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) {
        // 3. 创建涉及的 Agent
        HiringDecisionProposer decisionProposer = AgenticServices.agentBuilder(HiringDecisionProposer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("modelDecision")
                .build();

        // 2. 定义人在回路用于验证
        HumanInTheLoop humanValidator = AgenticServices.humanInTheLoopBuilder()
                .description("验证模型提出的招聘决策")
                .outputKey("finalDecision") // 由人类确认
                .responseProvider(scope -> {
                    System.out.println("AI 招聘助手建议: " + scope.readState("request"));
                    System.out.println("请确认最终决策。");
                    System.out.println("选项：邀请现场面试(I)，拒绝(R)，暂缓(H)");
                    System.out.print("> "); // 实际系统需要输入验证和错误处理
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                        return reader.readLine();
                    } catch (IOException e) {
                        throw new RuntimeException("读取输入失败", e);
                    }
                })
                .build();

        // 3. 将 Agent 串联成工作流
        UntypedAgent hiringDecisionWorkflow = AgenticServices.sequenceBuilder()
                .subAgents(decisionProposer, humanValidator)
                .outputKey("finalDecision")
                .build();

        // 4. 准备输入参数
        Map<String, Object> input = Map.of(
                "cvReview", new CvReview(0.85,
                        """
                                技术能力强，但缺少所需的 React 经验。
                                不过看起来学习和独立能力很强。文化匹配度好。
                                工作许可问题似乎可以解决。
                                薪资期望略高于计划预算。
                                决定推进现场面试。
                                """)
        );

        // 5. 运行工作流
        String finalDecision = (String) hiringDecisionWorkflow.invoke(input);

        System.out.println("\n=== 人类最终决策 ===");
        System.out.println("(邀请现场面试(I)，拒绝(R)，暂缓(H))\n");
        System.out.println(finalDecision);

        // 注意：人在回路和人工验证通常需要用户很长时间才能响应。
        // 在这种情况下，建议使用异步 Agent，这样它们不会阻塞工作流的其余部分
        // 那些可以在用户回答到达之前执行的步骤。
    }
}