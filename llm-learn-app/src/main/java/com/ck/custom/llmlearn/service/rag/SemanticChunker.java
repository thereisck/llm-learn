package com.ck.custom.llmlearn.service.rag;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author changkong
 * @date 2026/5/18 23:12
 **/
@Service
public class SemanticChunker {

    private final EmbeddingClient embeddingClient;

    public SemanticChunker(EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    public enum BreakpointStrategy {
        THRESHOLD, DIFF, PERCENTILE
    }

    public List<String> split(String text, BreakpointStrategy strategy, double param) {
        List<String> sentences = preSplit(text);
        if (sentences.size() <= 1) {
            return sentences;
        }
        List<double[]> embeddings = new ArrayList<>();
        for (String sentence : sentences) {
            embeddings.add(embeddingClient.embed(sentence));
        }
        List<Double> similarities = computeSimilarities(embeddings);
        List<Integer> breakpoints = detectBreakpoints(similarities, strategy, param);
        return mergeChunks(sentences, breakpoints);
    }

    //// 预切分 // 1. 按 。！？\n 拆分
    private List<String> preSplit(String text) {
        String[] parts = text.split("[。！？\\n]+");
        //2过滤空和过短
        List<String> sentences = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.length() > 5) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    // 计算相邻相似度
    private List<Double> computeSimilarities(List<double[]> embeddings) {
        List<Double> similarities = new ArrayList<>();
        for (int i = 0; i < embeddings.size() - 1; i++) {
            double sim = cosineSimilarity(embeddings.get(i), embeddings.get(i + 1));
            similarities.add(sim);
        }
        return similarities;
    }

    //// 辅助方法：余弦相似度
    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // 断点检测
    private List<Integer> detectBreakpoints(List<Double> similarities, BreakpointStrategy strategy, double param) {
        List<Integer> breakpoints = new ArrayList<>();
        switch (strategy) {
            case THRESHOLD:
                for (int i = 0; i < similarities.size(); i++) {
                    if (similarities.get(i) < param) {
                        //// 在 sentences[i] 和 sentences[i+1] 之间切
                        breakpoints.add(i);
                    }
                }
                break;
            case DIFF:
                for (int i = 1; i < similarities.size(); i++) {
                    double diff = Math.abs(similarities.get(i) - similarities.get(i - 1));
                    if (diff > param) {
                        breakpoints.add(i);
                    }
                }
                break;
            case PERCENTILE:
                List<Double> sorted = new ArrayList<>(similarities);
                Collections.sort(sorted);
                double percentileValue = computePercentile(sorted, param);
                double absoluteFloor = 0.5;
                double threshold = Math.max(percentileValue, absoluteFloor);
                for (int i = 0; i < similarities.size(); i++) {
                    if (similarities.get(i) < threshold) {
                        breakpoints.add(i);
                    }
                }
                break;
        }
        return breakpoints;
    }

    //// 辅助方法：计算百分位值（线性插值）
    private double computePercentile(List<Double> sorted, double percentile) {
        int n = sorted.size();
        double index = percentile / 100.0 * (n - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return sorted.get(lower);
        }
        return sorted.get(lower) + (index - lower) * (sorted.get(upper) - sorted.get(lower));
    }

    // 合并成chunk
    private List<String> mergeChunks(List<String> sentences, List<Integer> breakpoints) {
        List<String> chunks = new ArrayList<>();
        List<Integer> cuts = new ArrayList<>(breakpoints);
        Collections.sort(cuts);
        int start = 0;
        for (int cut : cuts) {
            // sentences[start..cut] 合成一个 chunk
            StringBuilder sb = new StringBuilder();
            for (int i = start; i <= cut; i++) {
                sb.append(sentences.get(i));
            }
            chunks.add(sb.toString());
            start = cut + 1;
        }
        // 处理最后一段
        if (start < sentences.size()) {
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < sentences.size(); i++) {
                sb.append(sentences.get(i));
            }
            chunks.add(sb.toString());
        }
        return chunks;
    }

}
