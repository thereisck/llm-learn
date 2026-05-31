package _8_non_ai_agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import domain.CvReview;

/**
 * 非 AI Agent，将多个简历审核结果聚合为综合审核。
 * 这演示了普通 Java 操作如何作为一等 Agent 在 agentic 工作流中使用，
 * 使它们可以与 AI Agent 互换使用。
 */
public class ScoreAggregator {

    @Agent(description = "将HR/经理/团队的审核结果聚合为综合审核", outputKey = "combinedCvReview")
    public CvReview aggregate(@V("hrReview") CvReview hr,
                             @V("managerReview") CvReview mgr,
                             @V("teamMemberReview") CvReview team) {

        System.out.println("ScoreAggregator 被调用，hrReview: " + hr +
                ", managerReview: " + mgr +
                ", teamMemberReview: " + team);

        double avgScore = (hr.score + mgr.score + team.score) / 3.0;
        
        String combinedFeedback = String.join("\n\n",
                "HR审核: " + hr.feedback,
                "经理审核: " + mgr.feedback,
                "团队成员审核: " + team.feedback
        );
        
        return new CvReview(avgScore, combinedFeedback);
    }
}