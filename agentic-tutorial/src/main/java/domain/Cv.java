package domain;

import dev.langchain4j.model.output.structured.Description;

public class Cv {
    @Description("候选人的技能，逗号分隔")
    private String skills;

    @Description("候选人的工作经验")
    private String professionalExperience;

    @Description("候选人的学习经历")
    private String studies;

    @Override
    public String toString() {
        return "简历:\n" +
                "技能 = \"" + skills + "\"\n" +
                "工作经验 = \"" + professionalExperience + "\"\n" +
                "学习经历 = \"" + studies + "\"\n";
    }
}