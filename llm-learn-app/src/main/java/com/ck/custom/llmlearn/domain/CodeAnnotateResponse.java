package com.ck.custom.llmlearn.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CodeAnnotateResponse {
    private String annotatedCode;
}
