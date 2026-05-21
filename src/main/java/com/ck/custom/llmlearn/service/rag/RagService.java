package com.ck.custom.llmlearn.service.rag;

import com.ck.custom.llmlearn.domain.rag.Chunk;
import com.ck.custom.llmlearn.domain.rag.EnterpriseRagResponse;
import com.ck.custom.llmlearn.domain.rag.RagQueryResponse;
import com.ck.custom.llmlearn.domain.rag.RerankResult;
import com.ck.custom.llmlearn.domain.rag.SearchResult;
import com.ck.custom.llmlearn.service.ContextCompressor;
import com.ck.custom.llmlearn.service.impl.ExtractiveCompressor;
import com.ck.custom.llmlearn.service.impl.SummaryCompressor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
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

    @Resource
    private ExtractiveCompressor extractiveCompressor;

    @Resource
    private SummaryCompressor summaryCompressor;

    @Resource
    private EnterpriseRagConfig enterpriseRagConfig;

    @Resource
    private SemanticChunker semanticChunker;

    private static final int RRF_K = 60;
    private static final String SEARCH_MODE_VECTOR = "vector";
    private static final String SEARCH_MODE_BM25 = "bm25";
    private static final String SEARCH_MODE_HYBRID = "hybrid";
    private static final String SEARCH_MODE_HYBRID_RERANK = "hybrid_rerank";
    private static final String SEARCH_MODE_MULTI_ROUTE = "multi_route";

    /** 企业文档是否已加载 */
    private volatile boolean enterpriseDocsLoaded = false;

    @PostConstruct
    public void init() {
        // 1. 只加载旧版 rag-note.md（快速启动）
        loadAndIndexDocument("docs/rag-note.md", "fixed");
        System.out.println("基础索引已加载，chunk数量: " + vectorStore.size()
                + ", bm25文档数量: " + bm25Searcher.size());
        System.out.println("企业文档请通过 POST /rag/enterprise/load 接口手动加载");
    }

    /** 手动加载企业文档（避免启动时阻塞） */
    public String loadEnterpriseDocs(String chunkStrategy) {
        if (enterpriseDocsLoaded) {
            return "企业文档已加载，无需重复加载。当前chunk数量: " + vectorStore.size();
        }
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            org.springframework.core.io.Resource[] resources = resolver.getResources("classpath:docs/enterprise/*.md");
            System.out.println("开始加载企业文档: " + resources.length + " 篇, 切分策略: " + chunkStrategy);
            for (org.springframework.core.io.Resource resource : resources) {
                String filename = resource.getFilename();
                String path = "docs/enterprise/" + filename;
                loadAndIndexDocument(path, chunkStrategy);
            }
            enterpriseDocsLoaded = true;
            String msg = "企业文档加载完成！共 " + resources.length + " 篇, 切分策略: " + chunkStrategy + "，总chunk数量: " + vectorStore.size() + ", bm25文档数量: " + bm25Searcher.size();
            System.out.println(msg);
            return msg;
        } catch (IOException e) {
            String msg = "企业文档加载失败: " + e.getMessage();
            System.out.println(msg);
            return msg;
        }
    }

    /** 加载单篇文档并索引到向量库和BM25 */
    private void loadAndIndexDocument(String source, String chunkStrategy) {
        String text = documentLoader.loadMarkdown(source);
        List<String> chunks;
        if ("semantic".equals(chunkStrategy)) {
            SemanticChunker.BreakpointStrategy strategy = SemanticChunker.BreakpointStrategy.valueOf(enterpriseRagConfig.getSemanticStrategy());
            chunks = semanticChunker.split(text, strategy, enterpriseRagConfig.getSemanticParam());
        } else {
            chunks = textSplitter.split(text);
        }
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            double[] embedding = embeddingClient.embed(chunkText);
            Chunk chunk = new Chunk(source, i, chunkText, embedding);
            vectorStore.addChunk(chunk);
            bm25Searcher.addChunk(chunk);
        }
        System.out.println("  已索引: " + source + " → " + chunks.size() + " chunks (strategy: " + chunkStrategy + ")");
    }

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

    public List<SearchResult> multiRouteQuery(String question) {
        // 路1：向量检索（语义相似）
        List<SearchResult> vectorResults = vectorSearch(question, 5, 0.5);
        // 路2：BM25检索（关键词匹配）
        List<SearchResult> bm25Results = bm25Searcher.search(question, 5);
        //    // 路3：精确匹配（问题中的关键词直接匹配chunk文本）
        List<SearchResult> exactResults = exactMatchSearch(question, 3);

        // 合并去重
        Map<String, SearchResult> merged = new LinkedHashMap<>();
        for (SearchResult r : vectorResults) {
            merged.merge(resultId(r), r, (old, cur) ->
                    old.getScore() > cur.getScore() ? old : cur);
        }
        for (SearchResult r : bm25Results) {
            merged.merge(resultId(r), r, (old, cur) ->
                    old.getScore() > cur.getScore() ? old : cur);
        }
        for (SearchResult r : exactResults) {
            merged.merge(resultId(r), r, (old, cur) ->
                    old.getScore() > cur.getScore() ? old : cur);
        }
        List<SearchResult> candidates = new ArrayList<>(merged.values());
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<String> candidateTexts = candidates.stream()
                .map(SearchResult::getText)
                .collect(Collectors.toList());
        List<RerankResult> reranked = rerankClient.rerank(question, candidateTexts, rerankTopN);
        return reranked.stream()
                .map(rr -> {
                    SearchResult origin = candidates.get(rr.getIndex());
                    return new SearchResult(
                            origin.getSource(),
                            origin.getChunkIndex(),
                            rr.getText(),
                            rr.getRelevanceScore());
                }).collect(Collectors.toCollection(ArrayList::new));
    }

    /** 路3：精确匹配检索（问题中的关键词直接命中chunk文本）*/
    private List<SearchResult> exactMatchSearch(String question, int limit) {
        List<String> queryTokens = bm25Searcher.tokenize(question);
        Set<String> meaningfulTokens = queryTokens.stream()
                .filter(t -> t.length() > 1)
                .collect(Collectors.toSet());
        if (meaningfulTokens.isEmpty()) {
            return List.of();
        }
        List<Chunk> allChunks = vectorStore.getAllChunks();
        List<SearchResult> results = new ArrayList<>();
        for (Chunk chunk : allChunks) {
            String text = chunk.getText().toLowerCase(Locale.ROOT);
            int hitCount = 0;
            for (String token : meaningfulTokens) {
                if (text.contains(token.toLowerCase(Locale.ROOT))) {
                    hitCount++;
                }
            }
            if (hitCount > 0) {
                double score = (double) hitCount / meaningfulTokens.size();
                results.add(new SearchResult(
                        chunk.getSource(),
                        chunk.getChunkIndex(),
                        chunk.getText(),
                        score));
            }
        }
        return results.stream()
                .sorted(Comparator.comparing(SearchResult::getScore).reversed())
                .limit(limit)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String resultId(SearchResult result) {
        return result.getSource() + "#" + result.getChunkIndex();
    }

    /** 只做检索不调LLM，用于实验对比 */
    public List<SearchResult> searchOnly(String question, double threshold, String searchMode) {
        String mode = normalizeSearchMode(searchMode);
        if (SEARCH_MODE_BM25.equals(mode)) {
            return bm25Searcher.search(question, topK);
        } else if (SEARCH_MODE_VECTOR.equals(mode)) {
            double[] queryEmbedding = embeddingClient.embed(question);
            return vectorStore.search(queryEmbedding, topK, threshold);
        } else if (SEARCH_MODE_HYBRID.equals(mode)) {
            return hybridSearch(question, topK);
        } else if (SEARCH_MODE_HYBRID_RERANK.equals(mode)) {
            List<SearchResult> candidates = hybridSearch(question, rerankCandidateK);
            if (candidates.isEmpty()) return List.of();
            List<String> candidateTexts = candidates.stream()
                    .map(SearchResult::getText).collect(Collectors.toList());
            List<RerankResult> reranked = rerankClient.rerank(question, candidateTexts, rerankTopN);
            return reranked.stream().map(rr -> {
                SearchResult origin = candidates.get(rr.getIndex());
                return new SearchResult(origin.getSource(), origin.getChunkIndex(), rr.getText(), rr.getRelevanceScore());
            }).collect(Collectors.toCollection(ArrayList::new));
        } else if (SEARCH_MODE_MULTI_ROUTE.equals(mode)) {
            return multiRouteQuery(question);
        } else {
            throw new IllegalArgumentException("不支持的检索模式: " + searchMode);
        }
    }

    public RagQueryResponse query(String question, double threshold) {
        return query(question, threshold, "vector", null);
    }

    private String normalizeSearchMode(String searchMode) {
        if (searchMode == null || searchMode.trim().isEmpty()) {
            return "vector";
        }
        return searchMode.trim().toLowerCase(Locale.ROOT);
    }

    public RagQueryResponse query(String question, double threshold, String searchMode, String compress) {
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
        } else if(SEARCH_MODE_MULTI_ROUTE.equals(searchMode)){
            results = multiRouteQuery(question);
        } else {
            throw new IllegalArgumentException("不支持的检索模式: " + searchMode);
        }
        if (results.isEmpty()) {
            return new RagQueryResponse(question,"资料中没有足够信息", results);
        }
        // 构建原始context
        String rawContext = results.stream()
                .map(r -> String.format("[来源:%s, chunk:%d, score:%.4f]%n%s",
                        r.getSource(), r.getChunkIndex(), r.getScore(), r.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));
        // 压缩环节
        String context;
        // 新参数
        String compressMode = normalizeCompressMode(compress);
        if ("none".equals(compressMode)) {
            context = rawContext;
        } else {
            List<String> chunkTexts = results.stream()
                    .map(SearchResult::getText)
                    .collect(Collectors.toList());
            ContextCompressor compressor = getCompressor(compressMode);
            String compressed = compressor.compress(chunkTexts, question);
            context = "【压缩摘要】\n" + compressed + "\n\n【原始检索结果】\n" + rawContext;
        }
        String prompt = buildPrompt(context, question);
        String answer = llmClient.chat(prompt);
        return new RagQueryResponse(question, answer, results);
    }

    private String normalizeCompressMode(String compress) {
        if (compress == null || compress.trim().isEmpty()) return "none";
        return compress.trim().toLowerCase(Locale.ROOT);
    }

    private ContextCompressor getCompressor(String mode) {
        if ("extractive".equals(mode)) return extractiveCompressor;
        if ("summary".equals(mode)) return summaryCompressor;
        throw new IllegalArgumentException("不支持的压缩模式: " + mode);
    }

    // ========================= 企业级查询 =========================

    /**
     * 企业级RAG查询 —— 整合Week4所有优化 + 知识库外检测
     *
     * 流程：
     * 1. hybrid_rerank 检索（vector+BM25+RRF → Rerank精排）
     * 2. 知识库外检测：最高score < outOfDomainThreshold → 拒绝回答
     * 3. 上下文压缩（可选）
     * 4. LLM生成答案
     *
     * @param question 用户问题
     * @param config   企业级配置（可null则用默认EnterpriseRagConfig）
     * @return EnterpriseRagResponse（含confidence和outOfDomain标记）
     */
    public EnterpriseRagResponse enterpriseQuery(String question, EnterpriseRagConfig config) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        if (config == null) {
            config = enterpriseRagConfig;
        }

        // ---- Step 1: 检索 ----
        List<SearchResult> results;
        String actualSearchMode = config.getSearchMode();

        if (SEARCH_MODE_HYBRID_RERANK.equals(actualSearchMode)) {
            // Hybrid + Rerank：先多召回候选，再Rerank精排
            List<SearchResult> candidates = hybridSearch(question, config.getRerankCandidateK());
            if (candidates.isEmpty()) {
                return new EnterpriseRagResponse(question, config.getOutOfDomainMessage(),
                        List.of(), 0.0, true, actualSearchMode, config.getCompressMode());
            }
            List<String> candidateTexts = candidates.stream()
                    .map(SearchResult::getText).collect(Collectors.toList());
            List<RerankResult> reranked = rerankClient.rerank(question, candidateTexts, config.getRerankTopN());
            results = reranked.stream().map(rr -> {
                SearchResult origin = candidates.get(rr.getIndex());
                return new SearchResult(origin.getSource(), origin.getChunkIndex(),
                        rr.getText(), rr.getRelevanceScore());
            }).collect(Collectors.toCollection(ArrayList::new));
        } else {
            // 其他模式：直接委托给searchOnly
            results = searchOnly(question, config.getThreshold(), actualSearchMode);
        }

        // ---- Step 2: 知识库外检测 ----
        double confidence = results.isEmpty() ? 0.0 : results.get(0).getScore();
        boolean outOfDomain = false;

        if (config.isOutOfDomainDetection() && confidence < config.getOutOfDomainThreshold()) {
            outOfDomain = true;
            return new EnterpriseRagResponse(question, config.getOutOfDomainMessage(),
                    results, confidence, outOfDomain, actualSearchMode, config.getCompressMode());
        }

        if (results.isEmpty()) {
            return new EnterpriseRagResponse(question, config.getOutOfDomainMessage(),
                    List.of(), 0.0, true, actualSearchMode, config.getCompressMode());
        }

        // ---- Step 3: 上下文压缩（可选） ----
        String rawContext = results.stream()
                .map(r -> String.format("[来源:%s, chunk:%d, score:%.4f]%n%s",
                        r.getSource(), r.getChunkIndex(), r.getScore(), r.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));

        String context;
        String actualCompressMode = "none";
        if (config.isEnableCompression()) {
            actualCompressMode = config.getCompressMode();
            List<String> chunkTexts = results.stream()
                    .map(SearchResult::getText).collect(Collectors.toList());
            ContextCompressor compressor = getCompressor(actualCompressMode);
            String compressed = compressor.compress(chunkTexts, question);
            context = "【压缩摘要】\n" + compressed + "\n\n【原始检索结果】\n" + rawContext;
        } else {
            context = rawContext;
        }

        // ---- Step 4: LLM生成 ----
        String prompt = buildEnterprisePrompt(context, question);
        String answer = llmClient.chat(prompt);

        return new EnterpriseRagResponse(question, answer, results, confidence,
                outOfDomain, actualSearchMode, actualCompressMode);
    }

    /** 企业级Prompt模板 —— 更强调严谨性和拒绝能力 */
    private String buildEnterprisePrompt(String context, String question) {
        return """
                你是星云科技的企业知识库问答助手。
                请严格根据【参考资料】回答用户问题。

                【重要规则】
                1. 只根据参考资料回答，绝不编造信息
                2. 如果参考资料中没有答案，明确说明：该问题不在知识库范围内
                3. 引用具体条款时，标注来源文档和章节号
                4. 涉及薪资、数字等精确信息时，必须引用原文数据
                5. 回答简洁有力，不要堆砌无关信息

                【参考资料】
                %s

                【用户问题】
                %s
                """.formatted(context, question);
    }
}
