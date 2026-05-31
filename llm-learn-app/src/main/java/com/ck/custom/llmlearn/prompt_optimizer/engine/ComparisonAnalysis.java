package com.ck.custom.llmlearn.prompt_optimizer.engine;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author changkong
 * @date 2026/4/30 16:00
 **/
@Data
public class ComparisonAnalysis {

    private String response1Id;
    private String response2Id;

    // 共同点
    private List<String> commonPoints;

    // 差异点
    private List<String> differences;

    // 各自优势
    private String advantage1;
    private String advantage2;

    // 推荐选择
    private String recommendation;
    private int preferredResponse; // 1 或 2

    // ========== 构造函数 ==========

    public ComparisonAnalysis() {
        this.commonPoints = new ArrayList<>();
        this.differences = new ArrayList<>();
    }

    public ComparisonAnalysis(String response1Id, String response2Id) {
        this();
        this.response1Id = response1Id;
        this.response2Id = response2Id;
    }

    // ========== 辅助方法 ==========

    /**
     * 添加共同点
     */
    public ComparisonAnalysis addCommonPoint(String point) {
        this.commonPoints.add(point);
        return this;
    }

    /**
     * 添加差异点
     */
    public ComparisonAnalysis addDifference(String difference) {
        this.differences.add(difference);
        return this;
    }

    /**
     * 设置推荐
     */
    public ComparisonAnalysis recommend(int preferredResponse, String reason) {
        this.preferredResponse = preferredResponse;
        this.recommendation = reason;
        return this;
    }

    /**
     * 获取推荐响应ID
     */
    public String getPreferredResponseId() {
        if (preferredResponse == 1) {
            return response1Id;
        } else if (preferredResponse == 2) {
            return response2Id;
        }
        return null;
    }
}
