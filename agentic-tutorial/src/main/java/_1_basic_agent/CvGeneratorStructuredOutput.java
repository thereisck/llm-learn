package _1_basic_agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import domain.Cv;

public interface CvGeneratorStructuredOutput {
    @UserMessage("""
            这是我的人生经历和职业发展轨迹，请将其转化为一份完整、规范的简历。
            不要虚构事实，也不要遗漏技能或经历。
            这份简历后续还会进一步优化，目前请确保内容完整。
            我的人生故事：{{lifeStory}}
            """)
    @Agent("根据用户提供的信息生成完整简历")
    Cv generateCv(@V("lifeStory") String userInfo);
}
