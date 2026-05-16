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
        return List.of();
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
}
