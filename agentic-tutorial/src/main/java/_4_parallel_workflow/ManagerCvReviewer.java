package _4_parallel_workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import domain.CvReview;

public interface ManagerCvReviewer {

    @Agent(name = "managerReviewer", description = "根据岗位描述审核简历，给出反馈和评分")
    @SystemMessage("""
            你是这个岗位的招聘经理：
            {{jobDescription}}
            你审核求职者的简历，决定邀请哪些申请者来面试。
            你对每份简历给出评分和反馈（优点和缺点）。
            你可以忽略缺失的地址和占位符等内容。

            重要提示：只返回有效的JSON格式，换行用\n表示，不要使用markdown格式或代码块。
            """)
    @UserMessage("""
            审核这份简历：{{candidateCv}}
            """)
    CvReview reviewCv(@V("candidateCv") String cv, @V("jobDescription") String jobDescription);
}