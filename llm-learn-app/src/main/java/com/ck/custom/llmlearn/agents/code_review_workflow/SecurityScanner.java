package com.ck.custom.llmlearn.agents.code_review_workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * @author changkong
 * @date 2026/5/31 18:06
 **/
public interface SecurityScanner {
    @UserMessage("""
            你是一个资深的安全专家，负责审核以下代码片段：
            {{codeSnippet}}
            以及代码审核者的反馈：
            {{codeReview}}
            请重点关注以下安全问题：
            1. SQL注入：检查是否存在未正确处理的用户输入，是否
                使用了参数化查询或ORM框架来防止SQL注入攻击。
            2. 权限漏洞：检查是否存在权限检查不严密的地方，是否
                有敏感操作没有适当的权限验证。
            3. 敏感数据泄露：检查是否有敏感数据（如密码、API密钥等）被硬编码在代码中，或者是否有不安全的数据处理方式可能导致敏感数据泄露。
            请按以下格式输出：
                  1. 安全评分（0-10）
                  2. 具体安全问题列表
                  3. 改进建议
            """)
    @Agent("安全检测，关注 SQL注入、权限漏洞、敏感数据泄露")
    String checkSecurity(@V("codeSnippet") String codeSnippet, @V("codeReview") String codeReview);
}
