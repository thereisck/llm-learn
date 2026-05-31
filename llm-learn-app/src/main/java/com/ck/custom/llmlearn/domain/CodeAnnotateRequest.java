package com.ck.custom.llmlearn.domain;

import lombok.Data;

@Data
public class CodeAnnotateRequest {
    private String code;
    private String language;
}
