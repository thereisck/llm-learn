package com.ck.custom.llmlearn.domain.rag;

import lombok.Data;

/**
 * @author changkong
 * @date 2026/5/10 18:36
 **/
@Data
public class RagQueryRequest {
    private String question;
    private double threshold;
    private String searchMode;
    private String compress;
}
