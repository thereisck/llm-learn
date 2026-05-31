package _4_parallel_workflow;

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

public class _4_Parallel_Workflow_Example {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 300);  // 控制模型调用日志的显示量
    }

     /**
     * 本示例演示如何实现3个并行的简历审核 Agent，同时评估简历。
     * 我们实现三个 Agent：
     * - ManagerCvReviewer（判断候选人能否胜任工作）
     *      输入：简历 + 岗位描述
     * - TeamMemberCvReviewer（判断候选人能否融入团队）
     *      输入：简历
     * - HrCvReviewer（从HR角度检查候选人是否合格）
     *      输入：简历 + HR要求
     */

    // 1. 定义驱动 Agent 的模型
    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        // 2. 定义三个子 Agent（在本包中）：
        //      - HrCvReviewer.java
        //      - ManagerCvReviewer.java
        //      - TeamMemberCvReviewer.java

        // 3. 使用 AgenticServices 创建所有 Agent
        HrCvReviewer hrCvReviewer = AgenticServices.agentBuilder(HrCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("hrReview") // 每次迭代都会被覆盖，也作为我们要观察的最终输出
                .build();

        ManagerCvReviewer managerCvReviewer = AgenticServices.agentBuilder(ManagerCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("managerReview") // 覆盖原始输入指令，每次迭代被覆盖并作为新指令
                .build();

        TeamMemberCvReviewer teamMemberCvReviewer = AgenticServices.agentBuilder(TeamMemberCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("teamMemberReview") // 覆盖原始输入指令，每次迭代被覆盖并作为新指令
                .build();

        // 4. 构建并行工作流
        var executor = Executors.newFixedThreadPool(3);  // 保留引用以便后续关闭

        UntypedAgent cvReviewGenerator = AgenticServices // 使用 UntypedAgent，除非你定义了结果组合 Agent
                .parallelBuilder()
                .subAgents(hrCvReviewer, managerCvReviewer, teamMemberCvReviewer) // 可以添加任意数量的 Agent
                .executor(executor) // 可选，默认使用内部缓存线程池，执行完成后自动关闭
                .outputKey("fullCvReview") // 这是我们要观察的最终输出
                .output(agenticScope -> {
                    // 从 agentic scope 中读取每个审核者的输出
                    CvReview hrReview = (CvReview) agenticScope.readState("hrReview");
                    CvReview managerReview = (CvReview) agenticScope.readState("managerReview");
                    CvReview teamMemberReview = (CvReview) agenticScope.readState("teamMemberReview");
                    // 返回合并的审核结果，评分取平均值（或任何你想要的聚合方式）
                    String feedback = String.join("\n",
                            "HR审核: " + hrReview.feedback,
                            "经理审核: " + managerReview.feedback,
                            "团队成员审核: " + teamMemberReview.feedback
                    );
                    double avgScore = (hrReview.score + managerReview.score + teamMemberReview.score) / 3.0;

                    return new CvReview(avgScore, feedback);
                        })
                .build();

        // 5. 从 resources/documents/ 中的文本文件加载原始参数
        String candidateCv = StringLoader.loadFromResource("/documents/tailored_cv.txt");
        String jobDescription = StringLoader.loadFromResource("/documents/job_description_backend.txt");
        String hrRequirements = StringLoader.loadFromResource("/documents/hr_requirements.txt");
        String phoneInterviewNotes = StringLoader.loadFromResource("/documents/phone_interview_notes.txt");

        // 6. 因为使用无类型 Agent，需要传入参数 Map
        Map<String, Object> arguments = Map.of(
                "candidateCv", candidateCv,
                "jobDescription", jobDescription
                ,"hrRequirements", hrRequirements
                ,"phoneInterviewNotes", phoneInterviewNotes
        );

        // 7. 调用组合 Agent 生成审核结果
        var review = cvReviewGenerator.invoke(arguments);

        // 8. 输出审核结果
        System.out.println("=== 简历审核结果 ===");
        System.out.println(review);

        // 9. 关闭线程池
        executor.shutdown();
   }
}