package com.ck.custom.llmlearn.agents.code_review_workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * @author changkong
 * @date 2026/5/31 17:58
 **/
public interface CodeReviewer {

    @UserMessage("""
            你是一个资深的代码审核者，负责审核以下代码片段：
            {{codeSnippet}}
            请按以下格式输出：
                  1. 代码质量评分（0-10）
                  2. 具体问题列表
                  3. 改进建议
            """)
    @Agent("审查代码质量、命名规范、设计模式使用，返回结构化反馈")
    String checkCode(@V("codeSnippet") String codeSnippet);
}
