package _4_parallel_workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import domain.CvReview;

public interface HrCvReviewer {

    @Agent(name = "hrReviewer", description = "审核简历是否符合HR要求，给出反馈和评分")
    @SystemMessage("""
            你是HR部门的员工，负责审核简历以填补以下要求的职位：
            {{hrRequirements}}
            你对每份简历给出评分和反馈（优点和缺点）。
            你可以忽略缺失的地址和占位符等内容。

            重要提示：只返回有效的JSON格式，换行用\n表示，不要使用markdown格式或代码块。
            """)
    @UserMessage("""
            审核这份简历：{{candidateCv}}，附带电话面试记录：{{phoneInterviewNotes}}
            """)
    CvReview reviewCv(@V("candidateCv") String cv, @V("phoneInterviewNotes") String phoneInterviewNotes, @V("hrRequirements") String hrRequirements);
}