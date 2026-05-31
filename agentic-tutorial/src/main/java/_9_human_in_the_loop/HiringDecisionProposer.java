package _9_human_in_the_loop;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import domain.CvReview;

public interface HiringDecisionProposer {
    
    @Agent("总结招聘决策供最终人工验证")
    @SystemMessage("""
        你将招聘原因总结在3行以内，
        供人类做出最终决定是否继续推进。
        """)
    @UserMessage("""
        招聘流程中各方的反馈：{{cvReview}}
        """)
    String propose(@V("cvReview") CvReview cvReview);
}