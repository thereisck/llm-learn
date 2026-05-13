package com.ck.custom.llmlearn.domain.rag;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * @author changkong
 * @date 2026/5/10 18:37
 **/
@Data
@AllArgsConstructor
public class RagQueryResponse {
    private String question;
    private String answer;
    private List<SearchResult> sources;
}
