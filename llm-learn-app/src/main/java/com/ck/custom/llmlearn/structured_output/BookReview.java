package com.ck.custom.llmlearn.structured_output;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Week7 Day2 - 结构化输出Demo
 * 书评POJO：让LLM把自由文本书评变成结构化Java对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookReview {

    /**
     * 书名
     */
    private String title;

    /**
     * 评分 1-10
     */
    private int rating;

    /**
     * 一句话总结
     */
    private String summary;

    /**
     * 优点列表
     */
    private List<String> pros;

    /**
     * 缺点列表
     */
    private List<String> cons;
}
