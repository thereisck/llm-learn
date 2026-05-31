package com.ck.custom.llmlearn.service.impl;

import com.ck.custom.llmlearn.service.ContextCompressor;
import com.ck.custom.llmlearn.service.rag.LlmClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author changkong
 * @date 2026/5/19 22:44
 **/
@Service
public class SummaryCompressor implements ContextCompressor {

    private LlmClient llmClient;

    public SummaryCompressor(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public String compress(List<String> chunks, String query) {
        // 步骤1：拼接所有chunk
        // 步骤1：拼接所有chunk
        String combinedText = String.join("\n\n", chunks);
        // 步骤2：构造摘要Prompt
        // 步骤2：构造摘要Prompt
        String systemPrompt = "你是一个信息提取专家。请对用户提供的文本进行摘要，只保留与用户问题直接相关的关键信息，去除无关内容。摘要应简洁精炼，保留关键事实、数据和逻辑。";
        String userPrompt = "用户问题：" + query + "\n\n参考资料：\n" + combinedText;
        // 步骤3：调用LLM生成摘要
        String summary = llmClient.chat(systemPrompt, userPrompt);
        // 步骤4：返回摘要文本
        return summary;
    }
}
