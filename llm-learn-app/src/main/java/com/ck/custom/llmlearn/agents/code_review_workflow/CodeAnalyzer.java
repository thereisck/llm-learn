package com.ck.custom.llmlearn.agents.code_review_workflow;

/**
 * @author changkong
 * @date 2026/6/14 17:30
 **/

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 代码分析Agent - 第二道关卡
 * 负责根据代码结构找出具体问题
 * 输出到AgenticScope的key: "issues"
 * 关键字段：issueCount（用于条件分支判断）
 *
 * ⚠️ 铁律：@UserMessage里的所有{{变量}}都必须声明@V
 *   codeSnippet = 初始输入（invoke的Map传入）
 *   codeStructure = scope自动提供（Reader的outputKey写入scope）
 *   虽然scope自动提供值，但声明不能省！
 */
public interface CodeAnalyzer {

    @UserMessage("""
            你是资深代码质量分析师，请分析以下代码：
            代码片段：{{codeSnippet}}
            代码结构摘要：{{codeStructure}}

            请找出以下类型的问题：
            1. 命名规范问题（变量名/方法名/类名）
            2. 设计模式使用不当
            3. 代码重复/冗余
            4. 异常处理缺失
            5. 性能隐患

            请用以下JSON格式输出（必须包含issueCount字段！）：
            {
              "issueCount": <问题总数，数字>,
              "issues": [
                {"id": 1, "category": "命名规范", "severity": "高/中/低", "description": "...", "location": "..."},
                ...
              ],
              "overallScore": <0-10的评分>
            }
            """)
    @Agent("分析代码质量问题，输出问题列表和数量，用于后续条件分支决策")
    String analyzeCode(@V("codeSnippet") String codeSnippet, @V("codeStructure") String codeStructure);
}
