package _5_conditional_workflow;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.chat.ChatModel;
import domain.CvReview;
import util.ChatModelProvider;
import util.StringLoader;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.io.IOException;
import java.util.Map;

public class _5a_Conditional_Workflow_Example {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 200);  // 控制模型调用日志的显示量
    }

    /**
     * 本示例演示条件分支 Agent 工作流。
     * 根据评分和候选人情况，我们将会：
     * - 调用 Agent 为候选人安排现场面试
     * - 调用 Agent 发送友好的拒绝邮件
     */

    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        // 2. 定义两个子 Agent（在本包中）：
        //      - EmailAssistant.java
        //      - InterviewOrganizer.java

        // 3. 使用 AgenticServices 创建所有 Agent
        EmailAssistant emailAssistant = AgenticServices.agentBuilder(EmailAssistant.class)
                .chatModel(CHAT_MODEL)
                .tools(new OrganizingTools()) // Agent 可以使用这里定义的所有工具
                .build();
        InterviewOrganizer interviewOrganizer = AgenticServices.agentBuilder(InterviewOrganizer.class)
                .chatModel(CHAT_MODEL)
                .tools(new OrganizingTools())
                .contentRetriever(RagProvider.loadHouseRulesRetriever()) // 这样可以为 Agent 添加 RAG
                .build();

        // 4. 构建条件分支工作流
        UntypedAgent candidateResponder = AgenticServices // 使用 UntypedAgent，除非你定义了结果组合 Agent
                .conditionalBuilder()
                .subAgents(agenticScope -> ((CvReview) agenticScope.readState("cvReview")).score >= 0.8, interviewOrganizer)
                .subAgents(agenticScope -> ((CvReview) agenticScope.readState("cvReview")).score < 0.8, emailAssistant)
                .build();
        // 补充说明：当定义了多个条件时，它们会按顺序执行。
        // 如果需要并行执行，请使用异步 Agent，参见 _5b_Conditional_Workflow_Example_Async

        // 5. 从 resources/documents/ 中的文本文件加载参数
        String candidateCv = StringLoader.loadFromResource("/documents/tailored_cv.txt");
        String candidateContact = StringLoader.loadFromResource("/documents/candidate_contact.txt");
        String jobDescription = StringLoader.loadFromResource("/documents/job_description_backend.txt");
        CvReview cvReviewFail = new CvReview(0.6, "简历不错，但缺少后端岗位所需的一些技术细节。");
        CvReview cvReviewPass = new CvReview(0.9, "简历优秀，完全符合后端岗位的所有要求。");

        // 5. 因为使用无类型 Agent，需要传入所有输入参数的 Map
        Map<String, Object> arguments = Map.of(
                "candidateCv", candidateCv,
                "candidateContact", candidateContact,
                "jobDescription", jobDescription,
                "cvReview", cvReviewPass // 改为 cvReviewFail 可以看到另一个分支
        );

        // 5. 调用条件分支 Agent，根据审核结果响应候选人
        candidateResponder.invoke(arguments);
        // 在本示例中，我们没有对 AgenticScope 做有意义的修改
        // 也没有有意义的输出需要打印，因为工具执行了最终动作。
        // 我们通过工具打印到控制台的动作（邮件发送、申请状态更新）来观察结果

        // 当你在调试模式观察日志时，工具调用结果 'success' 仍会发送给模型
        // 模型还会回复类似"邮件已发送给John Doe..."

        // 补充信息：如果工具是你的最后动作，且不想再回调模型，
        // 通常会添加 @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
        // https://docs.langchain4j.dev/tutorials/tools#returning-immediately-the-result-of-a-tool-execution-request
        // !!! 但在 agentic 工作流中，工具的 IMMEDIATE RETURN BEHAVIOR 不推荐使用，
        // 因为即时返回行为会将工具结果存入 AgenticScope，可能导致问题

        // 补充信息：本示例是通过代码检查条件来实现路由行为的。
        // 路由行为也可以通过让 LLM 决定最佳工具/Agent 来继续，可以使用：
        // - Supervisor Agent：在 Agent 上操作，参见 _7_supervisor_orchestration
        // - AiServices 作为工具，例如：
        // RouterService routerService = AiServices.builder(RouterAgent.class)
        //        .chatModel(model)
        //        .tools(medicalExpert, legalExpert, technicalExpert)
        //        .build();
        //
        // 最佳选项取决于你的用例：
        //
        // - 条件分支 Agent：硬编码调用条件
        // - AiServices 或 Supervisor：LLM 决定调用哪个专家
        //
        // - Agentic 方案（条件分支、Supervisor）：所有中间状态和调用链存储在 AgenticScope
        // - AiServices：很难跟踪调用链或中间状态

    }
}