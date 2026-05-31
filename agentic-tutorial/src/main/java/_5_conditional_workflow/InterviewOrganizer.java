package _5_conditional_workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface InterviewOrganizer {

    @Agent("为候选人安排现场面试")
    @SystemMessage("""
            你安排现场面试，给所有相关人员发送日历邀请，
            安排一周后的3小时面试，在上午进行。
            这是相关的岗位：{{jobDescription}}
            你还要给候选人发送祝贺邮件、面试详情和到场前需知事项。
            最后，将申请状态更新为'已邀请现场面试'。
            """)
    @UserMessage("""
            为这位候选人安排现场面试（适用外部访客政策）：{{candidateContact}}
            """)
    String organize(@V("candidateContact") String candidateContact, @V("jobDescription") String jobDescription);
}