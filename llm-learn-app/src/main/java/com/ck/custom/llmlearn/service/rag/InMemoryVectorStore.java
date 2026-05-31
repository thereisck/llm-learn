package com.ck.custom.llmlearn.service.rag;

import com.ck.custom.llmlearn.domain.rag.Chunk;
import com.ck.custom.llmlearn.domain.rag.SearchResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * @author changkong
 * @date 2026/5/10 20:35
 **/
@Component
public class InMemoryVectorStore {

    private final List<Chunk> chunks = new ArrayList<>();


    public void addChunk(Chunk chunk) {
        chunks.add(chunk);
    }

    public List<SearchResult> search(double[] queryEmbedding, int topK, double threshold) {
        return chunks.stream()
                .map(chunk -> new SearchResult(
                        chunk.getSource(),
                        chunk.getChunkIndex(),
                        chunk.getText(),
                        cosineSimilarity(queryEmbedding, chunk.getEmbedding())
                )).filter(result -> result.getScore() >= threshold)
                .sorted(Comparator.comparing(SearchResult::getScore).reversed())
                .limit(topK)
                .toList();
    }

    public int size() {
        return chunks.size();
    }

    private double  cosineSimilarity(double[] vecA, double[] vecB) {
        if (vecA.length != vecB.length) {
            throw new IllegalArgumentException("向量维度不匹配");
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vecA.length; i++) {
            dot += vecA[i] * vecB[i];
            normA += vecA[i] * vecA[i];
            normB += vecB[i] * vecB[i];
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public List<Chunk> getAllChunks() {
        return new ArrayList<>(chunks);
    }
}
