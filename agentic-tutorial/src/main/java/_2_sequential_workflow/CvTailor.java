package _2_sequential_workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface CvTailor {

    @Agent("根据特定指令定制简历")
    @SystemMessage("""
                这是一份需要根据岗位描述、反馈或其他指令进行定制的简历。
                你可以让简历看起来更符合要求，但不能虚构事实。
                可以去掉无关内容，使简历更贴合指令要求。
                目标是让求职者获得面试机会，并且能在面试中兑现简历内容。简历不要太长。
                主简历：{{masterCv}}
                """)
    @UserMessage("""
                定制简历的指令：{{instructions}}
                """)
    String tailorCv(@V("masterCv") String masterCv, @V("instructions") String instructions);
}
