package com.ck.custom.llmlearn.agents.code_review_workflow;

/**
 * @author changkong
 * @date 2026/6/14 17:28
 **/

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 代码读取Agent - 第一道关卡
 * 负责读取代码片段，提取结构信息（类/方法/字段）
 * 输出到AgenticScope的key: "codeStructure"
 */
public interface CodeReader {

    @UserMessage("""
            你是代码结构分析专家，请分析以下代码片段的结构：
            {{codeSnippet}}
            
            请输出以下信息：
            1. 类名和类结构概览
            2. 方法列表（名称、参数、返回值）
            3. 字段列表
            4. 代码行数估计
            5. 初步复杂度判断（简单/中等/复杂）
            
            请用结构化的JSON格式输出。
            """)
    @Agent("读取代码片段，提取类结构、方法列表、字段信息，输出代码结构摘要")
    String readCode(@V("codeSnippet") String codeSnippet);
}
