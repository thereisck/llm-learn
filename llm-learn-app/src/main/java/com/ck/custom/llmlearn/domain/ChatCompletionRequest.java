package com.ck.custom.llmlearn.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * @author changkong
 * @date 2026/4/12 22:39
 **/
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionRequest {
    private String model; // 模型名称，指定使用哪个模型进行对话生成。
    private List<Message> messages; // 消息列表，包含对话的历史消息。
    private String request_id; // 请求ID，用于标识本次请求。
    private Boolean do_sample; // 是否进行采样，用于控制生成文本的多样性。
    private Boolean stream; // 是否以流式方式返回结果，用于实时获取生成内容。
    private Float temperature; // 温度参数，用于控制生成文本的随机性。
    private Float top_p; // 核采样概率，用于控制生成文本的多样性。
    private Integer max_tokens; // 最大生成令牌数，限制生成文本的长度。
    private List<String> stop; // 停止序列，生成过程中遇到这些序列将停止生成。
    private List<Tool> tools; // 工具列表，包含可用于对话生成的工具。
    private Object tool_choice; // 工具选择，可以是字符串或对象，用于指定使用哪个工具。
    private String user_id; // 用户ID，用于标识用户。
}
