package com.ck.custom.llmlearn.service.rag;

import com.ck.custom.llmlearn.domain.rag.Chunk;
import com.ck.custom.llmlearn.domain.rag.SearchResult;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 
 * @author changkong
 * @date 2026/5/16 23:23
 **/
@Component
public class Bm25Searcher {

    private static final double DEFAULT_K1 = 1.2;
    private static final double DEFAULT_B = 0.75;
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("[\\p{IsHan}]+|[a-zA-Z0-9_+.#-]+");

    private final Map<String, Chunk> chunkMap = new HashMap<>();
    private final Map<String, Integer> documentLengths = new HashMap<>();
    private final Map<String, Map<String, Integer>> invertedIndex = new HashMap<>();
    private double totalDocumentLength = 0.0;

    public void addChunk(Chunk chunk) {
        //构建倒排索引
        String docId = documentId(chunk);
        if (chunkMap.containsKey(docId)) {
            return;
        }
        List<String> tokens = tokenize(chunk.getText());
        chunkMap.put(docId, chunk);
        documentLengths.put(docId, tokens.size());
        totalDocumentLength += tokens.size();
        Map<String, Integer> termFrequencies = new HashMap<>();
        for (String token : tokens) {
            termFrequencies.merge(token, 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : termFrequencies.entrySet()) {
            String token = entry.getKey();
            Integer frequency = entry.getValue();
            invertedIndex.computeIfAbsent(token, k -> new HashMap<>())
                    .put(docId,frequency);
        }
    }

    public List<SearchResult> search(String query, int topK) {
        //BM25 查询
        if (query == null || query.isEmpty() || chunkMap.isEmpty() || topK <= 0) {
            return List.of();
        }
        //查询切词
        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }
        //query token 去重
        Set<String> uniqueQueryTokens = new HashSet<>(queryTokens);
        //scores 保存每个文档总分
        Map<String, Double> scores = new HashMap<>();
        double averageDocumentLength = totalDocumentLength / chunkMap.size();
        for (String uniqueQueryToken : uniqueQueryTokens) {
            //从倒排索引拿 posting list
            Map<String, Integer> posting = invertedIndex.get(uniqueQueryToken);
            if (posting == null || posting.isEmpty()) {
                continue;
            }
            double tokenIdf = idf(chunkMap.size(), posting.size());
            for (Map.Entry<String, Integer> entry : posting.entrySet()) {
                String docId = entry.getKey();
                int termFrequency = entry.getValue();
                int documentLength = documentLengths.getOrDefault(docId, 0);
                //计算当前 token 对当前 doc 的分数
                double score = bm25TermScore(tokenIdf, termFrequency, documentLength, averageDocumentLength);
                scores.merge(docId, score, Double::sum);
            }
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    Chunk chunk = chunkMap.get(entry.getKey());
                    return new SearchResult(
                            chunk.getSource(),
                            chunk.getChunkIndex(),
                            chunk.getText(),
                            entry.getValue()
                    );
                })
                .toList();
    }

    public int size() {
        //返回文档数量
        return chunkMap.size();
    }

    private String documentId(Chunk chunk) {
        return chunk.getSource() + "#" + chunk.getChunkIndex();
    }

    public List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (containsHan(token)) {
                addChineseNgrams(token, tokens);
            } else if (token.length() > 1 || Character.isDigit(token.charAt(0))) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private boolean containsHan(String token) {
        return token.codePoints()
                .anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private void addChineseNgrams(String token, List<String> tokens) {
        // unigram：单字
        for (int i = 0; i < token.length(); i++) {
            tokens.add(token.substring(i, i + 1));
        }
        // bigram / trigram：二字、三字
        for (int n = 2; n <= 3; n++) {
            if(token.length() < n) {
                continue;
            }
            for (int i = 0; i <= token.length() - n; i++) {
                tokens.add(token.substring(i, i + n));
            }
        }
    }

    private double idf(int totalDocuments, int documentFrequency) {
        //log((N - df + 0.5) / (df + 0.5)) 负分会让结果不直观，所以我们用更工程化的版本
        return Math.log(1.0 + (totalDocuments - documentFrequency + 0.5) / (documentFrequency + 0.5));
    }

    private double bm25TermScore(double idf,
                                 int termFrequency,
                                 int documentLength,
                                 double averageDocumentLength) {
        if(termFrequency <=0 || documentLength <=0 || averageDocumentLength <=0) {
            return 0.0;
        }
        //长度归一化 这个文档比平均长度长一倍，所以词频要被更强地打折。
        //DEFAULT_B = 0.75
        //documentLength = 200
        //averageDocumentLength = 100
        //lengthNormalization = 1 - 0.75 + 0.75 × 200 / 100
        //                    = 0.25 + 1.5
        //                    = 1.75
        double lengthNormalization = 1.0 - DEFAULT_B
                + DEFAULT_B * (documentLength / averageDocumentLength);
        //tf × (k1 + 1)  分子
        double numerator = termFrequency * (DEFAULT_K1 + 1);
        //tf + k1 × (1 - b + b × |D| / avgdl) 次频饱和
        /**
         * idf = 1.2
         * termFrequency = 1
         * documentLength = 100
         * averageDocumentLength = 100
         * DEFAULT_K1 = 1.2
         * DEFAULT_B = 0.75
         * lengthNormalization = 1
         * numerator = 1 × 2.2 = 2.2
         * denominator = 1 + 1.2 × 1 = 2.2
         * score = 1.2 × 2.2 / 2.2 = 1.2
         *
         * 词出现一次，文档长度正常，得分≈IDF。
         *
         * 如果同一个词出现 3 次：
         *
         * termFrequency = 3
         * numerator = 3 × 2.2 = 6.6
         * denominator = 3 + 1.2 = 4.2
         * score = 1.2 × 6.6 / 4.2 = 1.885
         * 出现 3 次的分数不是 1 次的 3 倍
         *
         * 这是 BM25 的核心：词频饱和
         *
         * 如果出现 10 次：
         *
         * numerator = 10 × 2.2 = 22
         * denominator = 10 + 1.2 = 11.2
         * score = 1.2 × 22 / 11.2 = 2.357
         *
         * 从 3 次到 10 次，只从 1.885 涨到 2.357。
         *
         * 这就是防止“关键词堆砌”把分数刷爆
         */
        double denominator = termFrequency + DEFAULT_K1 * lengthNormalization;
        return idf * (numerator / denominator);
    }
}
