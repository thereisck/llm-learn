package com.ck.custom.llmlearn.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * @author changkong
 * @date 2026/4/12 22:48
 **/
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Usage implements Serializable {
    private long prompt_tokens; // 提示tokens数，表示输入文本中使用的tokens数量。
    private long completion_tokens; // 完成tokens数，表示生成文本中使用的tokens数量。
    private long total_tokens; // 总tokens数，表示整个交互过程中使用的tokens总数，包括提示和完成。
}