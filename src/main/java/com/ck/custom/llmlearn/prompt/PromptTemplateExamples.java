package com.ck.custom.llmlearn.prompt;


import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * @author changkong
 * @date 2026/4/29 17:38
 **/
public class PromptTemplateExamples {

    static class PromptTemplate_with_One_Variable_Example {
        public static void main(String[] args) {
            PromptTemplate promptTemplate = PromptTemplate.from("Say 'hi' in {{it}}.");
            Prompt prompt = promptTemplate.apply("German");
            System.out.println(prompt.text());
        }
    }

    static class PromptTemplate_With_Multiple_Variables_Example {
        public static void main(String[] args) {
            PromptTemplate from = PromptTemplate.from("Say '{{text}}' in {{language}}");
            Map<String, Object> vars = new HashMap<>();
            vars.put("text", "hi");
            vars.put("language", "chinese");
            Prompt apply = from.apply(vars);
            System.out.println(apply.text());
        }
    }
}
