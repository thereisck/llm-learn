package com.ck.custom.llmlearn.agents.code_review_workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * @author changkong
 * @date 2026/5/31 18:19
 **/
public interface CodeReviewWorkflow {

    @Agent("代码审查流水线：质量审核→安全检测→生成报告")
    String review(@V("codeSnippet") String codeSnippet);
}
