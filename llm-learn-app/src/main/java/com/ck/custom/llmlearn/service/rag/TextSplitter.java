package com.ck.custom.llmlearn.service.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author changkong
 * @date 2026/5/10 20:46
 **/
@Service
public class TextSplitter {
    @Value("${rag.chunk-size}")
    private int chunkSize;

    @Value("${rag.overlap}")
    private int overlap;

    public List<String> split(String text) {
        if (chunkSize < 0) {
            throw new IllegalArgumentException("chunkSize must be non-negative");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be non-negative and less than chunkSize");
        }
        List<String> chunks = new ArrayList<>();
        String normalized = text.replaceAll("\\s+", " ").trim();
        int start = 0;
        while (start < normalized.length()) {
          int end = Math.min(start + chunkSize, normalized.length());
          chunks.add(normalized.substring(start, end));
          if(end == normalized.length()) {
              break;
          }
          start = end - overlap;
        }
        return chunks;
    }
}
