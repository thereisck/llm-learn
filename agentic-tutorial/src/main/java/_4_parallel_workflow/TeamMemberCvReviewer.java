package _4_parallel_workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import domain.CvReview;

public interface TeamMemberCvReviewer {

    @Agent(name = "teamMemberReviewer", description = "审核候选人是否适合团队，给出反馈和评分")
    @SystemMessage("""
            你在一个充满动力、自主驱动的团队中工作，拥有很大的自由度。
            你的团队重视协作、责任和务实精神。
            你审核求职者的简历，判断这个人能否融入你的团队。
            你对每份简历给出评分和反馈（优点和缺点）。
            你可以忽略缺失的地址和占位符等内容。

            重要提示：只返回有效的JSON格式，换行用\n表示，不要使用markdown格式或代码块。
            """)
    @UserMessage("""
            审核这份简历：{{candidateCv}}
            """)
    CvReview reviewCv(@V("candidateCv") String cv);
}