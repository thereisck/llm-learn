package _7_supervisor_orchestration;

import _4_parallel_workflow.HrCvReviewer;
import _4_parallel_workflow.ManagerCvReviewer;
import _4_parallel_workflow.TeamMemberCvReviewer;
import _5_conditional_workflow.EmailAssistant;
import _5_conditional_workflow.InterviewOrganizer;
import _5_conditional_workflow.OrganizingTools;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.model.chat.ChatModel;
import util.ChatModelProvider;
import util.StringLoader;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.io.IOException;

/**
 * 到目前为止，我们构建的都是确定性工作流：
 * - 顺序、并行、条件、循环以及它们的组合。
 * 你也可以构建 Supervisor Agent 系统，其中 Agent 会动态决定调用哪些子 Agent 以及调用顺序。
 * 在本示例中，Supervisor 协调招聘工作流：
 * 他会运行 HR/经理/团队审核，然后安排面试或发送拒绝邮件。
 * 和组合工作流示例的第2部分类似，但现在是"自组织"的。
 * 注意：Supervisor 超级 Agent 可以像其他超级 Agent 类型一样在组合工作流中使用。
 * 重要提示：本示例用 GPT-4o-mini 运行大约需要50秒。你可以在 PRETTY 日志中持续看到执行过程。
 * 有加速执行的方法，参见文件末尾的注释。
 */
public class _7a_Supervisor_Orchestration {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 200);  // 控制模型调用日志的显示量
    }

    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        // 1. 定义所有子 Agent
        HrCvReviewer hrReviewer = AgenticServices.agentBuilder(HrCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("hrReview")
                .build();
        // 重要提示：如果多个 Agent 使用相同的方法名
        //（本例中：所有审核者都用 'reviewCv'），最好给 Agent 命名，例如：
        // @Agent(name = "managerReviewer", description = "根据岗位描述审核简历，给出反馈和评分")

        ManagerCvReviewer managerReviewer = AgenticServices.agentBuilder(ManagerCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("managerReview")
                .build();

        TeamMemberCvReviewer teamReviewer = AgenticServices.agentBuilder(TeamMemberCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("teamMemberReview")
                .build();

        InterviewOrganizer interviewOrganizer = AgenticServices.agentBuilder(InterviewOrganizer.class)
                .chatModel(CHAT_MODEL)
                .tools(new OrganizingTools())
                .build();

        EmailAssistant emailAssistant = AgenticServices.agentBuilder(EmailAssistant.class)
                .chatModel(CHAT_MODEL)
                .tools(new OrganizingTools())
                .build();

        // 2. 构建 Supervisor Agent
        SupervisorAgent hiringSupervisor = AgenticServices.supervisorBuilder()
                .chatModel(CHAT_MODEL)
                .subAgents(hrReviewer, managerReviewer, teamReviewer, interviewOrganizer, emailAssistant)
                .contextGenerationStrategy(SupervisorContextStrategy.CHAT_MEMORY_AND_SUMMARIZATION)
                .responseStrategy(SupervisorResponseStrategy.SUMMARY) // 我们想要执行摘要，而不是提取某个响应
                .supervisorContext("始终使用全部可用审核者。始终用英语回答。调用 Agent 时使用纯 JSON（不要反引号，换行用 \\n）。") // Supervisor 的行为上下文（可选）
                .build();
        // 重要提示：Supervisor 每次只调用1个 Agent，然后重新审查计划来决定下一个调用的 Agent
        // Supervisor 不支持 Agent 并行执行
        // 如果 Agent 标记为 async，Supervisor 会覆盖该设置（不异步执行）并发出警告

        // 3. 加载候选人简历和岗位描述
        String jobDescription = StringLoader.loadFromResource("/documents/job_description_backend.txt");
        String candidateCv = StringLoader.loadFromResource("/documents/tailored_cv.txt");
        String candidateContact = StringLoader.loadFromResource("/documents/candidate_contact.txt");
        String hrRequirements = StringLoader.loadFromResource("/documents/hr_requirements.txt");
        String phoneInterviewNotes = StringLoader.loadFromResource("/documents/phone_interview_notes.txt");

        // 开始计时
        long start = System.nanoTime();
        // 4. 用自然语言请求调用 Supervisor
        String result = (String) hiringSupervisor.invoke(
                "评估以下候选人：\n" +
                        "候选人简历：\n" + candidateCv + "\n\n" +
                        "候选人联系方式：\n" + candidateContact + "\n\n" +
                        "岗位描述：\n" + jobDescription + "\n\n" +
                        "HR要求：\n" + hrRequirements + "\n\n" +
                        "电话面试记录：\n" + phoneInterviewNotes
        );
        long end = System.nanoTime();
        double elapsedSeconds = (end - start) / 1_000_000_000.0;
        // 在日志中你会看到最终调用了 'done' Agent，这是 Supervisor 结束调用序列的方式

        System.out.println("=== Supervisor 运行完成，耗时 " + elapsedSeconds + " 秒 ===");
        System.out.println(result);
    }

    // 高级用法：
    // 参见 _7b_Supervisor_Orchestration_Advanced.java 了解：
    // - 有类型 Supervisor
    // - 上下文工程
    // - 输出策略
    // - 调用链观察

    // 关于延迟：
    // 本流程完整运行通常需要超过60秒。
    // 解决方案是使用快速推理提供商如 CEREBRAS，
    // 可以在10秒内运行整个流程，但会犯更多错误。
    // 要用 CEREBRAS 运行本示例，获取密钥（点击免费 API 密钥开始）
    // https://inference-docs.cerebras.ai/quickstart
    // 并保存到环境变量 "CEREBRAS_API_KEY"
    // 然后将第38行改为：
    // private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel("CEREBRAS");

}