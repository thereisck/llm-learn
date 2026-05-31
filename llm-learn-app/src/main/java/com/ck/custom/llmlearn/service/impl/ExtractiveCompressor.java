package com.ck.custom.llmlearn.service.impl;

import com.ck.custom.llmlearn.service.ContextCompressor;
import com.ck.custom.llmlearn.service.rag.EmbeddingClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author changkong
 * @date 2026/5/19 22:47
 **/
@Service
public class ExtractiveCompressor implements ContextCompressor {

    private EmbeddingClient embeddingClient;

    public ExtractiveCompressor(EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    @Override
    public String compress(java.util.List<String> chunks, String query) {
        // 步骤1：把每个chunk按句子拆分
        List<String> allSentences = new ArrayList<>();
        for (String chunk : chunks) {
            allSentences.addAll(splitToSentences(chunk));
        }
        if(allSentences.isEmpty()) {
            // 拆不出来就返回原文
            return String.join("\n\n", chunks);
        }
        // 步骤2：对每句话 + query 分别做embedding
        double[] queryEmbedding = embeddingClient.embed(query);
        List<double[]> sentenceEmbeddings = new ArrayList<>();
        for (String allSentence : allSentences) {
            sentenceEmbeddings.add(embeddingClient.embed(allSentence));
        }
        // 步骤3：计算每句话跟query的cosine相似度
        List<Double> similarities = new ArrayList<>();
        for (double[] sentenceEmbedding : sentenceEmbeddings) {
            similarities.add(cosineSimilarity(queryEmbedding, sentenceEmbedding));
        }
        // 步骤4：按相似度排序，取top-K句
        int k = Math.max(3, chunks.size());
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < similarities.size(); i++) {
            indices.add(i);
        }
        indices.sort((a, b) -> Double.compare(similarities.get(b), similarities.get(a)));
        // 步骤5：拼接输出
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < Math.min(k, indices.size()); i++) {
            selected.add(allSentences.get(indices.get(i)));
        }
        return String.join("\n", selected);
    }

    private List<String> splitToSentences(String text) {
        // 和昨天SemanticChunker的preSplit逻辑一样
        String[] parts = text.split("[。！？\\n]+");
        //2过滤空和过短
        List<String> sentences = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.length() >= 5) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    private double cosineSimilarity(double[] a, double[] b) {
        // 和昨天SemanticChunker的一样，复用即可
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
