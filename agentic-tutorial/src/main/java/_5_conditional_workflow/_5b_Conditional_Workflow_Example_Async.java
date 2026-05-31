package _5_conditional_workflow;

import _4_parallel_workflow.ManagerCvReviewer;
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

public class _5b_Conditional_Workflow_Example_Async {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 150);
    }

    /**
     * 本示例演示多个条件同时满足时，异步 Agent 可以并行执行以提高效率。
     * 在本示例中：
     * - 条件1：如果HR审核通过，简历交给经理审核
     * - 条件2：如果HR审核指示缺少信息，联系候选人补充信息
     */

    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        // 1. 创建所有异步 Agent
        ManagerCvReviewer managerCvReviewer = AgenticServices.agentBuilder(ManagerCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .async(true) // 异步 Agent
                .outputKey("managerReview")
                .build();
        EmailAssistant emailAssistant = AgenticServices.agentBuilder(EmailAssistant.class)
                .chatModel(CHAT_MODEL)
                .async(true)
                .tools(new OrganizingTools())
                .outputKey("sentEmailId")
                .build();
        InfoRequester infoRequester = AgenticServices.agentBuilder(InfoRequester.class)
                .chatModel(CHAT_MODEL)
                .async(true)
                .tools(new OrganizingTools())
                .outputKey("sentEmailId")
                .build();

        // 2. 构建异步条件分支工作流
        UntypedAgent candidateResponder = AgenticServices
                .conditionalBuilder()
                .subAgents(scope -> {
                    CvReview hrReview = (CvReview) scope.readState("cvReview");
                    return hrReview.score >= 0.8; // HR通过，交给经理审核
                }, managerCvReviewer)
                .subAgents(scope -> {
                    CvReview hrReview = (CvReview) scope.readState("cvReview");
                    return hrReview.score < 0.8; // HR不通过，发送拒绝邮件
                }, emailAssistant)
                .subAgents(scope -> {
                    CvReview hrReview = (CvReview) scope.readState("cvReview");
                    return hrReview.feedback.toLowerCase().contains("missing information:");
                }, infoRequester) // 需要时，请求候选人补充信息
                .output(agenticScope ->
                        (agenticScope.readState("managerReview", new CvReview(0, "无需经理审核"))).toString() +
                                "\n" + agenticScope.readState("sentEmailId", 0)
                ) // 最终输出是经理审核结果（如有）
                .build();

        // 3. 输入参数
        String candidateCv = StringLoader.loadFromResource("/documents/tailored_cv.txt");
        String candidateContact = StringLoader.loadFromResource("/documents/candidate_contact.txt");
        String jobDescription = StringLoader.loadFromResource("/documents/job_description_backend.txt");
        CvReview hrReview = new CvReview(
                0.85,
                """
                        候选人不错，薪资期望在范围内，能在期望时间范围内入职。
                        缺少信息：比利时工作授权状态的详细信息。
                        """
        );

        Map<String, Object> arguments = Map.of(
                "candidateCv", candidateCv,
                "candidateContact", candidateContact,
                "jobDescription", jobDescription,
                "cvReview", hrReview
        );


        // 4. 运行异步条件分支工作流
        candidateResponder.invoke(arguments);

        System.out.println("=== 异步条件分支工作流执行完成 ===");
    }
}