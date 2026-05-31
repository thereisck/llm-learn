package _3_loop_workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import domain.CvReview;

public interface CvReviewer {

    @Agent("评审简历，根据特定指令给出反馈和评分，考虑简历与岗位的匹配度")
    @SystemMessage("""
            你是这份岗位的招聘经理：
            {{jobDescription}}
            你需要评审求职者的简历，决定邀请哪些候选人来现场面试。
            你会给每份简历评分和反馈（包括优点和不足）。
            可以忽略地址缺失和占位符之类的小问题。
            """)
    @UserMessage("""
            请评审这份简历：{{cv}}
            """)
    CvReview reviewCv(@V("cv") String cv, @V("jobDescription") String jobDescription);
}
