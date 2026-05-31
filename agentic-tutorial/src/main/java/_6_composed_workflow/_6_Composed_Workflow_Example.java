package _6_composed_workflow;

import _1_basic_agent.CvGenerator;
import _3_loop_workflow.CvReviewer;
import _3_loop_workflow.ScoredCvTailor;
import _4_parallel_workflow.HrCvReviewer;
import _4_parallel_workflow.ManagerCvReviewer;
import _4_parallel_workflow.TeamMemberCvReviewer;
import _5_conditional_workflow.EmailAssistant;
import _5_conditional_workflow.InterviewOrganizer;
import _5_conditional_workflow.OrganizingTools;
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
import java.util.concurrent.Executors;

public class _6_Composed_Workflow_Example {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 300);  // 控制模型调用日志的显示量
    }

    /**
     * 每个 Agent——无论是单任务 Agent、顺序工作流还是其他——都是 Agent 对象。
     * 这使得 Agent 完全可组合。你可以：
     * - 将小 Agent 打包成超级 Agent
     * - 用子 Agent 分解任务
     * - 在任意层级混合顺序、并行、循环、Supervisor 等工作流
     * 在本示例中，我们将之前构建的组合 Agent（顺序、并行等）
     * 组合成两个更大的组合 Agent，编排整个招聘流程。
     */

    // 1. 定义驱动 Agent 的模型
    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        ////////////////// 候选人组合工作流 //////////////////////
        // 从人生经历 > 简历 > 审核 > 审核循环直到通过
        // 然后把简历发给公司

        // 1. 创建候选人工作流所需的所有 Agent
        CvGenerator cvGenerator = AgenticServices
                .agentBuilder(CvGenerator.class)
                .chatModel(CHAT_MODEL)
                .outputKey("cv")
                .build();

        ScoredCvTailor scoredCvTailor = AgenticServices
                .agentBuilder(ScoredCvTailor.class)
                .chatModel(CHAT_MODEL)
                .outputKey("cv")
                .build();

        CvReviewer cvReviewer = AgenticServices
                .agentBuilder(CvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("cvReview")
                .build();

        // 2. 创建简历改进的循环工作流
        UntypedAgent cvImprovementLoop = AgenticServices
                .loopBuilder()
                .subAgents(scoredCvTailor, cvReviewer)
                .outputKey("cv")
                .exitCondition(agenticScope -> {
                    CvReview review = (CvReview) agenticScope.readState("cvReview");
                    System.out.println("简历审核评分: " + review.score);
                    if (review.score >= 0.8)
                        System.out.println("简历评分达标，退出循环。\n");
                    return review.score >= 0.8;
                })
                .maxIterations(3)
                .build();

        // 3. 创建完整的候选人工作流：生成 > 审核 > 改进循环
        CandidateWorkflow candidateWorkflow = AgenticServices
                .sequenceBuilder(CandidateWorkflow.class)
                .subAgents(cvGenerator, cvReviewer, cvImprovementLoop)
                // 这里我们在 sequenceBuilder 中使用了组合 Agent cvImprovementLoop
                // 还需要在进入循环之前生成第一次审核，所以也需要 cvReviewer
                .outputKey("cv")
                .build();

        // 4. 加载输入数据
        String lifeStory = StringLoader.loadFromResource("/documents/user_life_story.txt");
        String jobDescription = StringLoader.loadFromResource("/documents/job_description_backend.txt");

        // 5. 执行候选人工作流
        String candidateResult = candidateWorkflow.processCandidate(lifeStory, jobDescription);
        // 注意：输入参数和中间参数都存储在一个 AgenticScope 中
        // 该 AgenticScope 对系统中的所有 Agent 可用，无论有多少层组合

        System.out.println("=== 候选人工作流完成 ===");
        System.out.println("最终简历: " + candidateResult);

        System.out.println("\n\n\n\n");

        ////////////////// 招聘团队组合工作流 //////////////////////
        // 收到候选人简历和联系方式的邮件。已完成电话HR面试。
        // 现在进行3个并行审核，然后将结果送入条件分支流程，邀请或拒绝。

        // 1. 创建招聘团队工作流所需的所有 Agent
        HrCvReviewer hrCvReviewer = AgenticServices
                .agentBuilder(HrCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("hrReview")
                .build();

        ManagerCvReviewer managerCvReviewer = AgenticServices
                .agentBuilder(ManagerCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("managerReview")
                .build();

        TeamMemberCvReviewer teamMemberCvReviewer = AgenticServices
                .agentBuilder(TeamMemberCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("teamMemberReview")
                .build();

        EmailAssistant emailAssistant = AgenticServices
                .agentBuilder(EmailAssistant.class)
                .chatModel(CHAT_MODEL)
                .tools(new OrganizingTools())
                .build();

        InterviewOrganizer interviewOrganizer = AgenticServices
                .agentBuilder(InterviewOrganizer.class)
                .chatModel(CHAT_MODEL)
                .tools(new OrganizingTools())
                .build();

        // 2. 创建并行审核工作流
        UntypedAgent parallelReviewWorkflow = AgenticServices
                .parallelBuilder()
                .subAgents(hrCvReviewer, managerCvReviewer, teamMemberCvReviewer)
                .executor(Executors.newFixedThreadPool(3))
                .outputKey("combinedCvReview")
                .output(agenticScope -> {
                    CvReview hrReview = (CvReview) agenticScope.readState("hrReview");
                    CvReview managerReview = (CvReview) agenticScope.readState("managerReview");
                    CvReview teamMemberReview = (CvReview) agenticScope.readState("teamMemberReview");
                    String feedback = String.join("\n",
                            "HR审核: " + hrReview.feedback,
                            "经理审核: " + managerReview.feedback,
                            "团队成员审核: " + teamMemberReview.feedback
                    );
                    double avgScore = (hrReview.score + managerReview.score + teamMemberReview.score) / 3.0;
                    System.out.println("最终平均简历审核评分: " + avgScore + "\n");
                    return new CvReview(avgScore, feedback);
                })
                .build();

        // 3. 创建最终决策的条件分支工作流
        UntypedAgent decisionWorkflow = AgenticServices
                .conditionalBuilder()
                .subAgents(agenticScope -> ((CvReview) agenticScope.readState("combinedCvReview")).score >= 0.8, interviewOrganizer)
                .subAgents(agenticScope -> ((CvReview) agenticScope.readState("combinedCvReview")).score < 0.8, emailAssistant)
                .build();

        // 4. 创建完整的招聘团队工作流：并行审核 → 决策
        HiringTeamWorkflow hiringTeamWorkflow = AgenticServices
                .sequenceBuilder(HiringTeamWorkflow.class)
                .subAgents(parallelReviewWorkflow, decisionWorkflow)
                .build();

        // 5. 加载输入数据
        String candidateCv = StringLoader.loadFromResource("/documents/tailored_cv.txt");
        String candidateContact = StringLoader.loadFromResource("/documents/candidate_contact.txt");
        String hrRequirements = StringLoader.loadFromResource("/documents/hr_requirements.txt");
        String phoneInterviewNotes = StringLoader.loadFromResource("/documents/phone_interview_notes.txt");

        // 将所有数据放入 Map 方便访问
        Map<String, Object> inputData = Map.of(
                "candidateCv", candidateCv,
                "candidateContact", candidateContact,
                "hrRequirements", hrRequirements,
                "phoneInterviewNotes", phoneInterviewNotes,
                "jobDescription", jobDescription
        );

        // 6. 执行招聘团队工作流
        hiringTeamWorkflow.processApplication(candidateCv, jobDescription, hrRequirements, phoneInterviewNotes, candidateContact);

        System.out.println("=== 招聘团队工作流完成 ===");
        System.out.println("并行审核完成，决策已做出");

        // 注意：随着工作流变得更复杂，确保输入、中间和输出参数名称唯一，
        // 以避免在共享 AgenticScope 中意外覆盖数据
    }
}