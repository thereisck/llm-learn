package com.ck.custom.llmlearn.agents.code_review_workflow;

/**
 * @author changkong
 * @date 2026/6/14 17:42
 **/

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 代码验证Agent - 第四道关卡（终审）
 * 负责验证改进建议的可行性，生成最终报告
 * 如果建议不合格，循环会回退到Suggester重新生成
 * 输出到AgenticScope的key: "finalReport"
 *
 * ⚠️ 铁律：@UserMessage里的所有{{变量}}都必须声明@V
 *   codeSnippet = 初始输入
 *   issues = scope自动提供（Analyzer的outputKey）
 *   suggestions = scope自动提供（Suggester的outputKey）
 */
public interface CodeValidator {

    @UserMessage("""
            你是代码审查终审专家，请验证以下改进建议的可行性：
            代码片段：{{codeSnippet}}
            发现的问题：{{issues}}
            改进建议：{{suggestions}}

            请逐条验证每个建议：
            1. 建议是否真的能解决对应问题？
            2. 代码示例是否正确且可运行？
            3. 建议是否会导致新的问题？
            4. 建议的优先级排序是否合理？

            请用以下JSON格式输出（必须包含accepted字段！）：
            {
              "accepted": true或false,
              "validationDetails": [
                {"suggestionId": 1, "valid": true/false, "reason": "验证理由"},
                ...
              ],
              "feedback": "如果不通过，给出具体的改进方向",
              "finalReport": "完整的代码审查报告，包含评分、问题总结、改进建议"
            }
            """)
    @Agent("验证改进建议的可行性，判定是否通过，如果不通过则反馈给Suggester重新迭代")
    String validateSuggestions(
            @V("codeSnippet") String codeSnippet,
            @V("issues") String issues,
            @V("suggestions") String suggestions
    );
}
