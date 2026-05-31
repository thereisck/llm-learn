package _5_conditional_workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import domain.CvReview;

public interface InfoRequester {

    @Agent("给候选人发邮件请求补充信息")
    @SystemMessage("""
            你给候选人发送一封友好的邮件，请求公司审核申请所需的额外信息。
            明确告知他们的申请仍在审核中。
            """)
    @UserMessage("""
            HR审核及缺失信息描述：{{cvReview}}
            
            候选人联系信息：{{candidateContact}}
            
            岗位描述：{{jobDescription}}
            """)
    String send(@V("candidateContact") String candidateContact, @V("jobDescription") String jobDescription, @V("cvReview") CvReview hrReview);
}