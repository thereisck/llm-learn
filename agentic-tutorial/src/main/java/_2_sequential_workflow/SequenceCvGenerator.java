package _2_sequential_workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;

import java.util.Map;

public interface SequenceCvGenerator {
    @Agent("根据用户提供的信息生成简历，并根据指令定制，不要太长，避免空行")
    ResultWithAgenticScope<Map<String, String>> generateTailoredCv(@V("lifeStory") String lifeStory, @V("instructions") String instructions);
}
