package domain;

import dev.langchain4j.model.output.structured.Description;

public class CvReview {
    @Description("评分0到1，表示你邀请该候选人面试的可能性")
    public double score;

    @Description("简历反馈：优点、需要改进的地方、缺少的技能、红旗警告等")
    public String feedback;

    public CvReview() {} // 反序列化需要无参构造函数，因为存在其他构造函数！

    public CvReview(double score, String feedback) {
        this.score = score;
        this.feedback = feedback;
    }

    @Override
    public String toString() {
        return "\n简历审核: " +
                " - 评分 = " + score +
                "\n- 反馈 = \"" + feedback + "\"\n";
    }
}