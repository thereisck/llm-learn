package _5_conditional_workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface EmailAssistant {

    @Agent("给未通过初审的候选人发送拒绝邮件，返回发送的邮件ID或0（如果无法发送）")
    @SystemMessage("""
            你给未通过初审的求职候选人发送一封友好的拒绝邮件。
            同时将申请状态更新为'已拒绝'。
            返回发送的邮件ID。
            """)
    @UserMessage("""
            被拒绝的候选人：{{candidateContact}}
            
            申请的职位：{{jobDescription}}
            """)
    int send(@V("candidateContact") String candidateContact, @V("jobDescription") String jobDescription);
}