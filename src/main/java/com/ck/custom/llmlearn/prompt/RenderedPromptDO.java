package com.ck.custom.llmlearn.prompt;

import dev.langchain4j.model.input.Prompt;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * @author changkong
 * @date 2026/4/29 23:57
 **/
@Data
@AllArgsConstructor
public class RenderedPromptDO {

    /**
     * 模板ID
     */
    private String templateId;

    /**
     * 渲染后的System Prompt（角色设定）
     */
    private String systemPrompt;

    /**
     * 渲染后的User Prompt（用户输入）
     */
    private String userPrompt;

    /**
     * 模板元数据
     */
    private Map<String, Object> metadata;

    /**
     * 获取完整Prompt（System + User）
     */
    public String getFullPrompt() {
        StringBuilder sb = new StringBuilder();

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            sb.append("[System]\n").append(systemPrompt).append("\n\n");
        }

        sb.append("[User]\n").append(userPrompt);

        return sb.toString();
    }

    /**
     * 转换为LangChain4j的Prompt对象
     * 可直接喂给ChatLanguageModel
     */
    public Prompt toLangChain4jPrompt() {
        // 这里需要构造包含SystemMessage和UserMessage的Prompt
        // LangChain4j的Prompt.from()方法不支持多消息，需要手动构造

        // 最简单方式：直接返回userPrompt
        return Prompt.from(userPrompt);
    }
}
