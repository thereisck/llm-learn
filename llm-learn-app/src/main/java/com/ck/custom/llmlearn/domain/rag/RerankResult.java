package com.ck.custom.llmlearn.domain.rag;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author changkong
 * @date 2026/5/17 19:48
 **/
@Data
@AllArgsConstructor
public class RerankResult {
    // 原始documents列表中的位置
    private int index;
    // Cross-Encoder打的相关性分数
    private double relevanceScore;
    // 文档原文
    private String text;
}
