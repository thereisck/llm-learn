package _6_composed_workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;


public interface CandidateWorkflow {
    @Agent("根据人生经历和岗位描述，生成完整简历，然后根据岗位描述调整简历，通过反馈循环迭代直到评分达标")
    String processCandidate(@V("lifeStory") String userInfo, @V("jobDescription") String jobDescription);
}