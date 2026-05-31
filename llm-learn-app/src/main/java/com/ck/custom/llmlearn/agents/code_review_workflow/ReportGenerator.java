package com.ck.custom.llmlearn.agents.code_review_workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * @author changkong
 * @date 2026/5/31 18:16
 **/
public interface ReportGenerator {

    @UserMessage("""
                你是一个资深的软件工程师，负责综合分析以下信息并生成一份详细的代码审核报告：
                1. 代码片段：{{codeSnippet}}
                2. 代码审核者的反馈：{{codeReview}}
                3. 安全专家的反馈：{{securityReview}}
                请综合以上信息，生成一份结构化的代码审核报告，内容应包括但不限于：
                1. 综合评分（0-10）
                2. 主要问题总结
                3. 改进建议
            """)
    @Agent("综合代码审核和安全检测结果，生成最终审查报告")
    String createReport(@V("codeSnippet") String codeSnippet, @V("codeReview") String codeReview, @V("securityReview") String securityReview);
}
