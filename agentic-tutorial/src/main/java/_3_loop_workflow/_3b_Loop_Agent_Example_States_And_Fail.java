package _3_loop_workflow;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.chat.ChatModel;
import domain.CvReview;
import util.ChatModelProvider;
import util.StringLoader;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class _3b_Loop_Agent_Example_States_And_Fail {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 300);  // 控制模型调用日志的显示量
    }

    /**
     * 这里构建与 3a 相同的循环 Agent，但这次我们会看到它失败——
     * 因为尝试将简历定制到一个不匹配的岗位描述上。
     * 我们还会在最终简历之外返回最新的评分和反馈，
     * 这样可以检查是否获得了足够的评分，以及这份简历是否值得投递。
     * 我们还演示了一个技巧：通过在每次退出条件检查时（即每次 Agent 调用后）
     * 将评审结果存入列表来查看中间状态（因为每次循环评审会被覆盖）。
     */

    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        // 1. 创建所有子 Agent（与之前相同）
        CvReviewer cvReviewer = AgenticServices.agentBuilder(CvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("cvReview") // 每次迭代都会更新，为下一次定制提供新反馈
                .build();
        ScoredCvTailor scoredCvTailor = AgenticServices.agentBuilder(ScoredCvTailor.class)
                .chatModel(CHAT_MODEL)
                .outputKey("cv") // 每次迭代都会更新，持续改进简历
                .build();

        // 2. 构建循环工作流，并在每次退出条件检查时存储评审结果
        // 了解退出条件是否满足还是仅达到最大迭代次数很重要
        // （例如，John 可能根本不想申请这个职位）。
        // 可以将输出变量改为包含最后的评分和反馈，循环结束后自行检查。
        // 还可以将中间值存入可变列表以便后续查看。
        // 下面的代码同时做了这两件事。
        List<CvReview> reviewHistory = new ArrayList<>();

        UntypedAgent reviewedCvGenerator = AgenticServices // 使用 UntypedAgent，除非你定义了结果组合 Agent，见下方
                .loopBuilder().subAgents(cvReviewer, scoredCvTailor) // 可以添加任意数量的 Agent，顺序很重要
                .outputKey("cvAndReview") // 这是我们要观察的最终输出
                .output(agenticScope -> {
                    Map<String, Object> cvAndReview = Map.of(
                            "cv", agenticScope.readState("cv"),
                            "finalReview", agenticScope.readState("cvReview")
                    );
                    return cvAndReview;
                })
                .exitCondition(scope -> {
                    CvReview review = (CvReview) scope.readState("cvReview");
                    reviewHistory.add(review); // 每次 Agent 调用时捕获评分+反馈
                    System.out.println("退出条件检查，当前评分=" + review.score);
                    return review.score >= 0.8;
                })
                .maxIterations(3) // 安全限制，避免退出条件永远不满足时的无限循环
                .build();

        // 3. Load the original arguments from text files in resources/documents/
        // - master_cv.txt
        // - job_description_backend.txt
        String masterCv = StringLoader.loadFromResource("/documents/master_cv.txt");
        String fluteJobDescription = "We are looking for a passionate flute teacher to join our music academy.";

        // 4. Because we use an untyped agent, we need to pass a map of arguments
        Map<String, Object> arguments = Map.of(
                "cv", masterCv, // start with the master CV, it will be continuously improved
                "jobDescription", fluteJobDescription
        );

        // 5. Call the composed agent to generate the tailored CV
        Map<String, Object> cvAndReview = (Map<String, Object>) reviewedCvGenerator.invoke(arguments);

        // You can observe the steps in the logs, for example:
        // Round 1 output: "content": "{\n  \"score\": 0.0,\n  \"feedback\": \"This CV is not suitable for the flute teacher position at our music academy...
        // Round 2 output: "content": "{\n  \"score\": 0.3,\n  \"feedback\": \"John's CV demonstrates strong soft skills such as communication, patience, and adaptability, which are important in a teaching role. However, the absence of formal music training or ...
        // Round 3 output: "content": "{\n  \"score\": 0.4,\n  \"feedback\": \"John Doe demonstrates strong soft skills and mentoring experience,...

        System.out.println("=== REVIEWED CV FOR FLUTE TEACHER ===");
        System.out.println(cvAndReview.get("cv")); // the final CV after the loop

        // now you get the finalReview in the output map so you can check
        // if the final score and feedback meet your requirements
        CvReview review = (CvReview) cvAndReview.get("finalReview");
        System.out.println("=== FINAL REVIEW FOR FLUTE TEACHER ===");
        System.out.println("CV" + (review.score >= 0.8 ? " passes" : " does not pass") + " with score=" + review.score);
        System.out.println("Final feedback: " + review.feedback);

        // in reviewHistory you find the full history of reviews
        System.out.println("=== FULL REVIEW HISTORY FOR FLUTE TEACHER ===");
        System.out.println(reviewHistory);

    }
}
