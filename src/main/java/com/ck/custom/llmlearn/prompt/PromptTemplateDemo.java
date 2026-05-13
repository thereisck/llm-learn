package com.ck.custom.llmlearn.prompt;

import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;

import java.util.Map;

/**
 * LangChain4j 自带 PromptTemplate，专门为LLM应用设计
 * @author changkong
 * @date 2026/4/29 21:59
 **/
public class PromptTemplateDemo {

    public static void main(String[] args) {
        String templateText = """
                你是资深 {{role}} 专家。
                请审查以下 {{language}} 代码:
                <br>
                {{language}}
                {{code}}
                </br>
                关注点：{{focus}}
                """;
        PromptTemplate template = PromptTemplate.from(templateText);
        Prompt apply = template.apply(Map.of(
                "role", "安全",
                "language", "Java",
                "code", """
                        public void login(String username, String password) {
                            String query = "SELECT * FROM users WHERE username = '" + username + "' AND password = '" + password + "'";
                            // 执行查询
                        }
                        """,
                "focus", "SQL 注入风险"
        ));
        System.out.println(apply.text());
    }
}
