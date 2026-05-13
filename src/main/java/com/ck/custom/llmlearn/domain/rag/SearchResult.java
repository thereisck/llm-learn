package com.ck.custom.llmlearn.domain.rag;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author changkong
 * @date 2026/5/10 18:37
 **/
@Data
@AllArgsConstructor
public class SearchResult {
    private String source;
    private int chunkIndex;
    private String text;
    private double score;
}
