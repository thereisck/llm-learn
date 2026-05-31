package _7_supervisor_orchestration;

import _4_parallel_workflow.HrCvReviewer;
import _4_parallel_workflow.ManagerCvReviewer;
import _4_parallel_workflow.TeamMemberCvReviewer;
import _5_conditional_workflow.EmailAssistant;
import _5_conditional_workflow.InterviewOrganizer;
import _5_conditional_workflow.OrganizingTools;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.model.chat.ChatModel;
import util.ChatModelProvider;
import util.StringLoader;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 高级 Supervisor 示例，使用显式 AgenticScope 检查不断演变的上下文
 */
public class _7b_Supervisor_Orchestration_Advanced {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 200);
    }

    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    /**
     * 在本示例中，我们构建了一个与 _7a_Supervisor_Orchestration 类似的 Supervisor，
     * 但探索了 Supervisor 的多个额外特性：
     * - 有类型 Supervisor
     * - 上下文工程
     * - 输出策略
     * - 调用链观察
     * - 上下文演变检查
     */
    public static void main(String[] args) throws IOException {

        // 1. 定义子 Agent
        HrCvReviewer hrReviewer = AgenticServices.agentBuilder(HrCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .build();
        ManagerCvReviewer managerReviewer = AgenticServices.agentBuilder(ManagerCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .build();
        TeamMemberCvReviewer teamReviewer = AgenticServices.agentBuilder(TeamMemberCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .build();
        InterviewOrganizer interviewOrganizer = AgenticServices.agentBuilder(InterviewOrganizer.class)
                .chatModel(CHAT_MODEL)
                .tools(new OrganizingTools())
                .outputKey("response")
                .build();
        EmailAssistant emailAssistant = AgenticServices.agentBuilder(EmailAssistant.class)
                .chatModel(CHAT_MODEL)
                .tools(new OrganizingTools())
                .outputKey("response")
                .build();

        // 2. 构建 Supervisor

        HiringSupervisor hiringSupervisor = AgenticServices
                .supervisorBuilder(HiringSupervisor.class)
                .chatModel(CHAT_MODEL)
                .subAgents(hrReviewer, managerReviewer, teamReviewer, interviewOrganizer, emailAssistant)
                .contextGenerationStrategy(SupervisorContextStrategy.CHAT_MEMORY_AND_SUMMARIZATION)
                // 根据你的 Supervisor 需要了解子 Agent 的哪些操作，
                // 可以选择 contextGenerationStrategy: CHAT_MEMORY, SUMMARIZATION, 或 CHAT_MEMORY_AND_SUMMARIZATION
                .responseStrategy(SupervisorResponseStrategy.SCORED) // 此策略使用评分模型来决定最后一个响应还是摘要更好地解决用户请求
                // 此处的 output 函数会覆盖 responseStrategy
                .supervisorContext("策略：始终先检查HR，必要时升级，拒绝低匹配度的候选人。")
                .build();

        // 3. 加载输入数据
        String jobDescription = StringLoader.loadFromResource("/documents/job_description_backend.txt");
        String candidateCv = StringLoader.loadFromResource("/documents/tailored_cv.txt");
        String candidateContact = StringLoader.loadFromResource("/documents/candidate_contact.txt");
        String hrRequirements = StringLoader.loadFromResource("/documents/hr_requirements.txt");
        String phoneInterviewNotes = StringLoader.loadFromResource("/documents/phone_interview_notes.txt");

        String request = "评估这位候选人，安排面试或发送拒绝邮件。\n"
                + "候选人简历：\n" + candidateCv + "\n"
                + "候选人联系方式：\n" + candidateContact + "\n"
                + "岗位描述：\n" + jobDescription + "\n"
                + "HR要求：\n" + hrRequirements + "\n"
                + "电话面试记录：\n" + phoneInterviewNotes;

        // 4. 调用 Supervisor
        long start = System.nanoTime();
        ResultWithAgenticScope<String> decision = hiringSupervisor.invoke(request, "经理技术审核最重要。");
        long end = System.nanoTime();

        System.out.println("=== 招聘 Supervisor 完成，耗时 " + ((end - start) / 1_000_000_000.0) + "秒 ===");
        System.out.println(decision.result());

        // 打印收集的上下文
        System.out.println("\n=== 上下文对话 ===");
        System.out.println(decision.agenticScope().contextAsConversation()); // 将在下个版本中生效

    }
}