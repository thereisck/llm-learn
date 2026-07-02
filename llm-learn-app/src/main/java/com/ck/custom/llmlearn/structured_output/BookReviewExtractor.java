package com.ck.custom.llmlearn.structured_output;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Week7 Day2 - Demo1: AiServices自动推断结构化输出
 * 
 * 最简洁的方式：接口方法直接返回POJO类型
 * AiServices自动推断Schema + 自动反序列化
 * 
 * ⚠️ 铁律：方法必须有@UserMessage定义用户消息模板
 *   没有@UserMessage → IllegalConfigurationException
 */
public interface BookReviewExtractor {

    @SystemMessage("你是一个专业的书评分析助手。从用户提供的书评文本中提取结构化信息。输出必须是JSON格式。")
    @UserMessage("请从以下书评文本中提取书名、评分(1-10)、一句话总结、优点列表和缺点列表。书评内容：{{reviewText}}")
    BookReview extractReview(@V("reviewText") String reviewText);
}
