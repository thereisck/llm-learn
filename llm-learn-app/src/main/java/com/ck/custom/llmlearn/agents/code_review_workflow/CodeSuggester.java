package com.ck.custom.llmlearn.agents.code_review_workflow;

/**
 * @author changkong
 * @date 2026/6/14 17:33
 **/

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 代码建议Agent - 第三道关卡
 * 负责针对问题给出改进建议
 * 输出到AgenticScope的key: "suggestions"
 *
 * ⚠️ 铁律：@UserMessage里的所有{{变量}}都必须声明@V
 *   codeSnippet = 初始输入（invoke的Map传入）
 *   codeStructure = scope自动提供（Reader的outputKey）
 *   issues = scope自动提供（Analyzer的outputKey）
 *   已去掉scope里没有的reviewMode/previousSuggestions/validationFeedback
 */
public interface CodeSuggester {

    @UserMessage("""
            你是代码改进专家，请为以下代码问题给出具体的改进建议：
            代码片段：{{codeSnippet}}
            代码结构：{{codeStructure}}
            发现的问题：{{issues}}

            请用以下JSON格式输出：
            {
              "suggestions": [
                {"issueId": 1, "suggestion": "具体改进方案", "codeExample": "改进后的代码示例", "priority": "高/中/低"},
                ...
              ],
              "qualityScore": <建议质量自评0-10>
            }
            """)
    @Agent("针对代码问题生成改进建议")
    String suggestImprovements(
            @V("codeSnippet") String codeSnippet,
            @V("codeStructure") String codeStructure,
            @V("issues") String issues
    );
}
