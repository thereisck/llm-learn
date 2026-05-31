package _8_non_ai_agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import domain.CvReview;

/**
 * 非 AI Agent，根据综合评分更新申请状态。
 * 这演示了普通 Java 操作如何作为一等 Agent 在 agentic 工作流中使用，
 * 使它们可以与 AI Agent 互换使用。
 */
public class StatusUpdate {

    @Agent(description = "根据评分更新申请状态")
    public void update(@V("combinedCvReview") CvReview aggregateCvReview) {
        double score = aggregateCvReview.score;
        System.out.println("StatusUpdate 被调用，评分: " + score);

        if (score >= 8.0) {
            // 模拟数据库更新，仅用于演示
            System.out.println("申请状态已更新为: 已邀请面试");
        } else {
            // 模拟数据库更新，仅用于演示
            System.out.println("申请状态已更新为: 已拒绝");
        }
    }
}