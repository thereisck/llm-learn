package _8_non_ai_agents;

import _4_parallel_workflow.HrCvReviewer;
import _4_parallel_workflow.ManagerCvReviewer;
import _4_parallel_workflow.TeamMemberCvReviewer;
import _5_conditional_workflow.EmailAssistant;
import _5_conditional_workflow.InterviewOrganizer;
import _5_conditional_workflow.OrganizingTools;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.model.chat.ChatModel;
import domain.CvReview;
import util.ChatModelProvider;
import util.StringLoader;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;

public class _8_Non_AI_Agents {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 100);  // 控制模型调用日志的显示量
    }

    /**
     * 本示例演示如何在 agentic 工作流中使用非 AI Agent（纯 Java 操作）。
     * 非 AI Agent 就是普通方法，但可以像其他任何类型的 Agent 一样使用。
     * 它们非常适合确定性操作，如计算、数据转换和聚合——这些步骤你不需要 LLM 参与。
     * 越多的步骤能外包给非 AI Agent，你的工作流就越快、越准确、越便宜。
     * 非 AI Agent 比工具更适用于需要强制某些步骤确定性的工作流。
     * 在本例中，我们希望审核者的聚合评分由确定性计算得出，而非 LLM。
     * 同时，根据聚合评分确定性更新数据库中的申请状态。
     */

    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        // 1. 在本包中定义 ScoreAggregator 非 AI Agent

        // 2. 构建并行审核步骤的 AI 子 Agent
        HrCvReviewer hrReviewer = AgenticServices.agentBuilder(HrCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("hrReview")
                .build();

        ManagerCvReviewer managerReviewer = AgenticServices.agentBuilder(ManagerCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("managerReview")
                .build();

        TeamMemberCvReviewer teamReviewer = AgenticServices.agentBuilder(TeamMemberCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("teamMemberReview")
                .build();

        // 3. 构建组合并行 Agent
        var executor = Executors.newFixedThreadPool(3);  // 保留引用以便后续关闭

        UntypedAgent parallelReviewWorkflow = AgenticServices
                .parallelBuilder()
                .subAgents(hrReviewer, managerReviewer, teamReviewer)
                .executor(executor)
                .build();

        // 4. 构建包含非 AI Agent 的完整工作流
        UntypedAgent collectFeedback = AgenticServices
                .sequenceBuilder()
                .subAgents(
                        parallelReviewWorkflow,
                        new ScoreAggregator(), // 非 AI Agent 不需要 AgenticServices builder。outputKey 'combinedCvReview' 在类中定义
                        new StatusUpdate(), // 接收 'combinedCvReview' 作为输入，不需要输出
                        AgenticServices.agentAction(agenticScope -> { // 另一种添加非 AI Agent 的方式，可以直接操作 AgenticScope
                            CvReview review = (CvReview) agenticScope.readState("combinedCvReview");
                            agenticScope.writeState("scoreAsPercentage", review.score * 100); // 当来自不同系统的 Agent 通信时，输出转换通常是必需的
                        })
                )
                .outputKey("scoreAsPercentage") // outputKey 在 ScoreAggregator.java 的非 AI Agent 注解中定义
                .build();

        // 5. 加载输入数据
        String candidateCv = StringLoader.loadFromResource("/documents/tailored_cv.txt");
        String candidateContact = StringLoader.loadFromResource("/documents/candidate_contact.txt");
        String hrRequirements = StringLoader.loadFromResource("/documents/hr_requirements.txt");
        String phoneInterviewNotes = StringLoader.loadFromResource("/documents/phone_interview_notes.txt");
        String jobDescription = StringLoader.loadFromResource("/documents/job_description_backend.txt");

        Map<String, Object> arguments = Map.of(
                "candidateCv", candidateCv,
                "candidateContact", candidateContact,
                "hrRequirements", hrRequirements,
                "phoneInterviewNotes", phoneInterviewNotes,
                "jobDescription", jobDescription
        );

        // 6. 调用工作流
        double scoreAsPercentage = (double) collectFeedback.invoke(arguments);
        executor.shutdown();

        System.out.println("=== 评分百分比 ===");
        System.out.println(scoreAsPercentage);
        // 从日志中可以看到，申请状态也已相应更新

    }
}