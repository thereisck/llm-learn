package com.ck.custom.llmlearn.service.rag;

import com.ck.custom.llmlearn.domain.rag.Chunk;
import com.ck.custom.llmlearn.domain.rag.RagQueryResponse;
import com.ck.custom.llmlearn.domain.rag.SearchResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
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

    @Value("${rag.top-k}")
    private int topK;

    @PostConstruct
    public void init() {
        String source = "docs/rag-note.md";
        String text = documentLoader.loadMarkdown(source);
        List<String> chunks = textSplitter.split(text);
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            double[] embedding = embeddingClient.embed(chunkText);
            vectorStore.addChunk(new Chunk(source, i, chunkText, embedding));
        }
        System.out.println("已加载文档并构建向量索引，chunk数量: " + vectorStore.size());
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

    public RagQueryResponse query(String question, double threshold) {
        if(question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        double[] queryEmbedding = embeddingClient.embed(question);
        List<SearchResult> results = vectorStore.search(queryEmbedding, topK, threshold);
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
