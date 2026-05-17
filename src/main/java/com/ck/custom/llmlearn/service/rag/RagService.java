package com.ck.custom.llmlearn.service.rag;

import com.ck.custom.llmlearn.domain.rag.Chunk;
import com.ck.custom.llmlearn.domain.rag.RagQueryResponse;
import com.ck.custom.llmlearn.domain.rag.RerankResult;
import com.ck.custom.llmlearn.domain.rag.SearchResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author changkong
 * @date 2026/5/10 20:55
 **/
@Service
public class RagService {
    @Resource
    private DocumentLoader documentLoader;

    @Resource
    private TextSplitter textSplitter;

    @Resource
    private EmbeddingClient embeddingClient;

    @Resource
    private LlmClient llmClient;

    @Resource
    private InMemoryVectorStore vectorStore;

    @Resource
    private Bm25Searcher bm25Searcher;

    @Resource
    private RerankClient rerankClient;

    // Rerank前召回多少候选
    @Value("${rag.rerank-top-k:20}")
    private int rerankCandidateK;

    // Rerank后保留多少
    @Value("${rag.rerank-top-n:3}")
    private int rerankTopN;

    @Value("${rag.top-k}")
    private int topK;

    private static final int RRF_K = 60;
    private static final String SEARCH_MODE_VECTOR = "vector";
    private static final String SEARCH_MODE_BM25 = "bm25";
    private static final String SEARCH_MODE_HYBRID = "hybrid";
    private static final String SEARCH_MODE_HYBRID_RERANK = "hybrid_rerank";

    @PostConstruct
    public void init() {
        String source = "docs/rag-note.md";
        String text = documentLoader.loadMarkdown(source);
        List<String> chunks = textSplitter.split(text);
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            double[] embedding = embeddingClient.embed(chunkText);
            Chunk chunk = new Chunk(source, i, chunkText, embedding);
            vectorStore.addChunk(chunk);
            bm25Searcher.addChunk(chunk);
        }
        System.out.println("已加载文档并构建检索索引，chunk数量: "
                + vectorStore.size()
                + ", bm25文档数量: "
                + bm25Searcher.size());    }

    private String buildPrompt(String context, String question) {
        return """
                你是一个严谨的知识库问答助手。
                请只根据【参考资料】回答用户问题。
                如果参考资料中没有答案，请回答：资料中没有足够信息。

                【参考资料】
                %s

                【用户问题】
                %s

                【回答要求】
                1. 先给出直接答案
                2. 再列出依据
                3. 不要编造参考资料中不存在的信息
                4. 如果引用资料，请说明来自哪个 chunk
                """.formatted(context, question);
    }

    private List<SearchResult> vectorSearch(String question, int limit, double threshold) {
        double[] queryEmbedding = embeddingClient.embed(question);
        return vectorStore.search(queryEmbedding, limit, threshold);
    }

    private List<SearchResult> hybridSearch(String question, int limit) {
        //如果 hybrid 每路都只取 top3，候选太少，融合没意义
        int candidateSize = Math.max(limit * 5, 20);
        //为什么 vector threshold 用 0.0？hybrid 阶段要尽量先多召回候选，然后交给 RRF 排序
        //threshold = 0.6 可能向量检索阶段就把一些候选过滤掉了，RRF 根本没机会融合。
        List<SearchResult> vectorResults = vectorSearch(question, candidateSize, 0.0);
        List<SearchResult> bm25Results = bm25Searcher.search(question, candidateSize);
        return reciprocalRankFusion(vectorResults, bm25Results, limit);
    }

    private List<SearchResult> reciprocalRankFusion(List<SearchResult> vectorResults,
                                                    List<SearchResult> bm25Results,
                                                    int limit) {
        Map<String, SearchResult> candidates = new LinkedHashMap<>();
        Map<String, Double> scores = new LinkedHashMap<>();
        accumulateRrfScore(vectorResults, candidates, scores);
        accumulateRrfScore(bm25Results, candidates, scores);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    SearchResult origin = candidates.get(entry.getKey());
                    return new SearchResult(
                            origin.getSource(),
                            origin.getChunkIndex(),
                            origin.getText(),
                            entry.getValue());
                }).collect(Collectors.toCollection(ArrayList::new));
    }

    private void accumulateRrfScore(List<SearchResult> results,
                                    Map<String, SearchResult> candidates,
                                    Map<String, Double> scores) {
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            String id = resultId(result);
            candidates.putIfAbsent(id, result);
            //为什么是 i + 1？
            //Java list 下标从 0 开始：i = 0 表示第一名
            //i = 1 表示第二名 但 RRF 公式里的 rank 从 1 开始
            double rrfScore = 1.0 / (RRF_K + i + 1);
            scores.merge(id, rrfScore, Double::sum);
        }
    }

    private String resultId(SearchResult result) {
        return result.getSource() + "#" + result.getChunkIndex();
    }

    public RagQueryResponse query(String question, double threshold) {
        return query(question, threshold, "vector");
    }

    private String normalizeSearchMode(String searchMode) {
        if (searchMode == null || searchMode.trim().isEmpty()) {
            return "vector";
        }
        return searchMode.trim().toLowerCase(Locale.ROOT);
    }

    public RagQueryResponse query(String question, double threshold, String searchMode) {
        if(question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        String mode = normalizeSearchMode(searchMode);

        List<SearchResult> results;
        if (SEARCH_MODE_BM25.equals(mode)) {
            results = bm25Searcher.search(question, topK);
        } else if (SEARCH_MODE_VECTOR.equals(mode)) {
            double[] queryEmbedding = embeddingClient.embed(question);
            results = vectorStore.search(queryEmbedding, topK, threshold);
        } else if (SEARCH_MODE_HYBRID.equals(mode)) {
            results = hybridSearch(question, topK);
        } else if (SEARCH_MODE_HYBRID_RERANK.equals(mode)){
            // 先Hybrid召回候选（多召回一些给Rerank做精排）
            List<SearchResult> candidates = hybridSearch(question, rerankCandidateK);
            if (candidates.isEmpty()) {
                return new RagQueryResponse(question, "资料中没有足够信息", candidates);
            }
            //// 把候选文本提取出来，送入Rerank
            List<String> candidateTexts = candidates.stream()
                    .map(SearchResult::getText)
                    .collect(Collectors.toList());
            List<RerankResult> reranked = rerankClient.rerank(question, candidateTexts, rerankTopN);
            //用Rerank结果替换原来的排序
            results = reranked.stream()
                    .map(rr -> {
                        SearchResult origin = candidates.get(rr.getIndex());
                        return new SearchResult(
                                origin.getSource(),
                                origin.getChunkIndex(),
                                rr.getText(),
                                rr.getRelevanceScore()
                        );
                    }).collect(Collectors.toCollection(ArrayList::new));
        } else {
            throw new IllegalArgumentException("不支持的检索模式: " + searchMode);
        }
        if (results.isEmpty()) {
            return new RagQueryResponse(question,"资料中没有足够信息", results);
        }
        String context = results.stream()
                .map(r -> String.format("[来源:%s, chunk:%d, score:%.4f]%n%s",
                        r.getSource(), r.getChunkIndex(), r.getScore(), r.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));
        String prompt = buildPrompt(context, question);
        String answer = llmClient.chat(prompt);
        return new RagQueryResponse(question, answer, results);
    }
}
