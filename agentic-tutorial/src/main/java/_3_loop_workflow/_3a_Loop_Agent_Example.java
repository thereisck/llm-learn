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
import java.util.Map;

public class _3a_Loop_Agent_Example {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 300);  // 控制模型调用日志的显示量
    }

    /**
     * 本示例演示如何实现一个 CvReviewer Agent，与 CvTailor Agent 组成循环。
     * 我们将实现两个 Agent：
     * - ScoredCvTailor（接收简历和 CvReview（反馈/指令+评分），进行定制）
     * - CvReviewer（接收定制后的简历和岗位描述，返回 CvReview 对象（反馈+评分））
     * 此外，当评分超过阈值（如 0.7）时循环结束（退出条件）
     */

    // 1. 定义驱动 Agent 的模型
    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel("OPENAI");

    public static void main(String[] args) throws IOException {

        // 2. 在本包中定义两个子 Agent：
        //      - CvReviewer.java
        //      - CvTailor.java

        // 3. 使用 AgenticServices 创建所有 Agent
        CvReviewer cvReviewer = AgenticServices.agentBuilder(CvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("cvReview") // 每次迭代都会更新，为下一次定制提供新反馈
                .build();
        ScoredCvTailor scoredCvTailor = AgenticServices.agentBuilder(ScoredCvTailor.class)
                .chatModel(CHAT_MODEL)
                .outputKey("cv") // 每次迭代都会更新，持续改进简历
                .build();

        // 4. 构建循环工作流
        UntypedAgent reviewedCvGenerator = AgenticServices // 使用 UntypedAgent，除非你定义了结果组合 Agent，见 _2_Sequential_Agent_Example
                .loopBuilder().subAgents(cvReviewer, scoredCvTailor) // 可以添加任意数量的 Agent，顺序很重要
                .outputKey("cv") // 这是我们要观察的最终输出（改进后的简历）
                .exitCondition(agenticScope -> {
                            CvReview review = (CvReview) agenticScope.readState("cvReview");
                    System.out.println("检查退出条件，当前评分=" + review.score); // 记录中间评分
                            return review.score >= 0.1;
                        }) // 基于 CvReviewer 给出的评分的退出条件，评分 > 0.8 时满意
                // 注意：退出条件在每个 Agent 调用后都会检查，不只是整个循环结束后
                .maxIterations(3) // 安全限制，避免退出条件永远不满足时的无限循环
                .build();

        // 5. 从 resources/documents/ 中的文本文件加载原始参数
        // - master_cv.txt
        // - job_description_backend.txt
        String masterCv = StringLoader.loadFromResource("/documents/master_cv.txt");
        String jobDescription = StringLoader.loadFromResource("/documents/job_description_backend.txt");

        // 5. 因为使用无类型 Agent，需要传入参数 Map
        Map<String, Object> arguments = Map.of(
                "cv", masterCv, // 从主简历开始，会持续改进
                "jobDescription", jobDescription
        );

        // 5. 调用组合 Agent 生成定制简历
        String tailoredCv = (String) reviewedCvGenerator.invoke(arguments);

        // 6. 打印生成的简历
        System.out.println("=== 经过评审的简历（无类型） ===");
        System.out.println((String) tailoredCv);

        // 这个简历可能在第一次定制+评审后就通过了
        // 如果想看失败的情况，可以尝试长笛教师的岗位描述
        // 如示例 3b 所示，那里我们还会检查简历的中间状态
        // 并获取最终评审和评分。

    }
}
