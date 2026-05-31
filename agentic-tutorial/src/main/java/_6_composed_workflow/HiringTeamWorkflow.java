package _6_composed_workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

public interface HiringTeamWorkflow {
    @Agent("根据简历、电话面试和岗位描述，邀请或拒绝候选人")
    void processApplication(@V("candidateCv") String candidateCv,
                          @V("jobDescription") String jobDescription, 
                          @V("hrRequirements") String hrRequirements, 
                          @V("phoneInterviewNotes") String phoneInterviewNotes, 
                          @V("candidateContact") String candidateContact);
}