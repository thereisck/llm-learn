package com.ck.custom.llmlearn.agents.monitoring;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 智能助手Agent接口（agentic风格）
 *
 * ⚠️ 关键区别：agentic风格 vs AiServices风格
 *
 * AiServices风格（之前的SmartAssistant）：
 *   @SystemMessage("...") + chat(@UserMessage String) → AiServices.builder()
 *   只能做单Agent，不能组合
 *
 * Agentic风格（现在的MonitoredAssistant）：
 *   @Agent("描述") + @UserMessage("模板{{var}}") + @V("var") → AgenticServices.agentBuilder()
 *   可以组合成串行/并行/循环/条件workflow
 *
 * 为什么要改成agentic风格？
 * 因为AgentListener是agentic模块的功能，只能通过AgenticServices.agentBuilder()注入！
 * AiServices.builder()没有.listener()方法！
 */
public interface MonitoredAssistant {

    @Agent("智能助手，可以使用工具帮助用户。当可以通过工具解决时必须调用工具。只有工具无法解决的问题，才用自己的知识回答。")
    @UserMessage("{{userMessage}}")
    String chat(@V("userMessage") String userMessage);
}
