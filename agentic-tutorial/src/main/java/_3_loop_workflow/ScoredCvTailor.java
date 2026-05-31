package _3_loop_workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import domain.CvReview;

public interface ScoredCvTailor {

    @Agent("根据特定指令定制简历")
    @SystemMessage("""
            这是一份需要根据岗位描述、反馈或其他指令进行定制的简历。
            你可以让简历看起来更符合要求，但不能虚构事实。
            可以去掉无关内容，使简历更贴合指令要求。
            目标是让求职者获得面试机会，并且能在面试中兑现简历内容。
            当前简历：{{cv}}
            """)
    @UserMessage("""
            定制简历的指令和反馈：
            （再次提醒，不要虚构原始简历中没有的事实。
            如果求职者不适合该岗位，突出最接近的现有特征，
            但不要编造事实）
            评审结果：{{cvReview}}
            """)
    String tailorCv(@V("cv") String cv, @V("cvReview") CvReview cvReview);
}
