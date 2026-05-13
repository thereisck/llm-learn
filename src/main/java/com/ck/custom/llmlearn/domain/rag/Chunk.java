package com.ck.custom.llmlearn.domain.rag;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author changkong
 * @date 2026/5/10 18:34
 **/
@Data
@AllArgsConstructor
public class Chunk {
    private String source;
    private int chunkIndex;
    private String text;
    private double[] embedding;
}
