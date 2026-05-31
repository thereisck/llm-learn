package _6_composed_workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

public interface CvImprovementLoop {
    @Agent("通过迭代式简历调整和审核来改进简历，直到评分达标")
    String improveCv(@V("cv") String cv, @V("jobDescription") String jobDescription);
}