package com.ck.custom.llmlearn.contoller;

import com.ck.custom.llmlearn.domain.rag.EnterpriseRagResponse;
import com.ck.custom.llmlearn.domain.rag.RagQueryRequest;
import com.ck.custom.llmlearn.domain.rag.RagQueryResponse;
import com.ck.custom.llmlearn.domain.rag.SearchResult;
import com.ck.custom.llmlearn.service.rag.EnterpriseRagConfig;
import com.ck.custom.llmlearn.service.rag.RagService;
import com.ck.custom.llmlearn.service.rag.SemanticChunker;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author changkong
 * @date 2026/5/10 21:20
 **/
@RestController
@RequestMapping("/rag")
public class RagController {

    @Resource
    private RagService ragService;

    @Resource
    private SemanticChunker semanticChunker;

    @Resource
    private EnterpriseRagConfig enterpriseRagConfig;

    /** 原有标准RAG查询 */
    @PostMapping("/query")
    public RagQueryResponse query(@RequestBody RagQueryRequest request) {
        return ragService.query(request.getQuestion(), request.getThreshold(), request.getSearchMode(), request.getCompress());
    }

    /** 只做检索不调LLM */
    @PostMapping("/search")
    public List<SearchResult> searchOnly(@RequestBody RagQueryRequest request) {
        return ragService.searchOnly(request.getQuestion(), request.getThreshold(), request.getSearchMode());
    }

    // ========================= 企业级接口 =========================

    /**
     * 企业级RAG查询 —— 使用EnterpriseRagConfig最佳配置
     * 整合 hybrid_rerank + 知识库外检测 + 上下文压缩
     */
    @PostMapping("/enterprise/query")
    public EnterpriseRagResponse enterpriseQuery(@RequestBody RagQueryRequest request) {
        return ragService.enterpriseQuery(request.getQuestion(), null);
    }

    /**
     * 企业级RAG查询 —— 自定义配置（用于对比实验）
     * 可覆盖searchMode、threshold、compress等参数
     */
    @PostMapping("/enterprise/query/custom")
    public EnterpriseRagResponse enterpriseQueryCustom(@RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        // 构建自定义配置，基于默认配置但允许覆盖
        EnterpriseRagConfig customConfig = new EnterpriseRagConfig();
        // 复制默认配置
        customConfig.setChunkStrategy(enterpriseRagConfig.getChunkStrategy());
        customConfig.setSemanticStrategy(enterpriseRagConfig.getSemanticStrategy());
        customConfig.setSemanticParam(enterpriseRagConfig.getSemanticParam());
        customConfig.setSearchMode(enterpriseRagConfig.getSearchMode());
        customConfig.setThreshold(enterpriseRagConfig.getThreshold());
        customConfig.setTopK(enterpriseRagConfig.getTopK());
        customConfig.setRerankCandidateK(enterpriseRagConfig.getRerankCandidateK());
        customConfig.setRerankTopN(enterpriseRagConfig.getRerankTopN());
        customConfig.setEnableCompression(enterpriseRagConfig.isEnableCompression());
        customConfig.setCompressMode(enterpriseRagConfig.getCompressMode());
        customConfig.setExactMatchBoost(enterpriseRagConfig.isExactMatchBoost());
        customConfig.setOutOfDomainDetection(enterpriseRagConfig.isOutOfDomainDetection());
        customConfig.setOutOfDomainThreshold(enterpriseRagConfig.getOutOfDomainThreshold());
        customConfig.setOutOfDomainMessage(enterpriseRagConfig.getOutOfDomainMessage());
        customConfig.setDocDir(enterpriseRagConfig.getDocDir());
        // 允许请求参数覆盖
        if (body.containsKey("searchMode")) customConfig.setSearchMode((String) body.get("searchMode"));
        if (body.containsKey("threshold")) customConfig.setThreshold(Double.parseDouble(body.get("threshold").toString()));
        if (body.containsKey("enableCompression")) customConfig.setEnableCompression(Boolean.parseBoolean(body.get("enableCompression").toString()));
        if (body.containsKey("compressMode")) customConfig.setCompressMode((String) body.get("compressMode"));
        if (body.containsKey("outOfDomainDetection")) customConfig.setOutOfDomainDetection(Boolean.parseBoolean(body.get("outOfDomainDetection").toString()));
        if (body.containsKey("outOfDomainThreshold")) customConfig.setOutOfDomainThreshold(Double.parseDouble(body.get("outOfDomainThreshold").toString()));
        return ragService.enterpriseQuery(question, customConfig);
    }

    /**
     * 批量企业级查询 —— 用于对比实验，一次性跑多个问题
     */
    @PostMapping("/enterprise/batch")
    public List<EnterpriseRagResponse> enterpriseBatchQuery(@RequestBody Map<String, Object> body) {
        List<String> questions = (List<String>) body.get("questions");
        EnterpriseRagConfig config = null; // 使用默认配置
        if (body.containsKey("searchMode") || body.containsKey("threshold")) {
            // 有自定义参数时构建自定义配置
            config = new EnterpriseRagConfig();
            // 复制默认值...
            config.setSearchMode(enterpriseRagConfig.getSearchMode());
            config.setThreshold(enterpriseRagConfig.getThreshold());
            config.setEnableCompression(enterpriseRagConfig.isEnableCompression());
            config.setCompressMode(enterpriseRagConfig.getCompressMode());
            config.setRerankCandidateK(enterpriseRagConfig.getRerankCandidateK());
            config.setRerankTopN(enterpriseRagConfig.getRerankTopN());
            config.setOutOfDomainDetection(enterpriseRagConfig.isOutOfDomainDetection());
            config.setOutOfDomainThreshold(enterpriseRagConfig.getOutOfDomainThreshold());
            config.setOutOfDomainMessage(enterpriseRagConfig.getOutOfDomainMessage());
            config.setExactMatchBoost(enterpriseRagConfig.isExactMatchBoost());
            if (body.containsKey("searchMode")) config.setSearchMode((String) body.get("searchMode"));
            if (body.containsKey("threshold")) config.setThreshold(Double.parseDouble(body.get("threshold").toString()));
            if (body.containsKey("enableCompression")) config.setEnableCompression(Boolean.parseBoolean(body.get("enableCompression").toString()));
            if (body.containsKey("compressMode")) config.setCompressMode((String) body.get("compressMode"));
            if (body.containsKey("outOfDomainDetection")) config.setOutOfDomainDetection(Boolean.parseBoolean(body.get("outOfDomainDetection").toString()));
            if (body.containsKey("outOfDomainThreshold")) config.setOutOfDomainThreshold(Double.parseDouble(body.get("outOfDomainThreshold").toString()));
        }
        List<EnterpriseRagResponse> responses = new ArrayList<>();
        for (String question : questions) {
            responses.add(ragService.enterpriseQuery(question, config));
        }
        return responses;
    }

    /** 语义切分接口 */
    @PostMapping("/chunk/semantic")
    public List<String> semanticChunk(
            @RequestBody Map<String, Object> body) {
        String text = (String) body.get("text");
        String strategyStr = (String) body.getOrDefault("strategy", "PERCENTILE");
        SemanticChunker.BreakpointStrategy strategy = SemanticChunker.BreakpointStrategy.valueOf(strategyStr);
        double param = Double.parseDouble(body.getOrDefault("param", "25.0").toString());
        return semanticChunker.split(text, strategy, param);
    }

    /** 手动加载企业文档（避免启动时阻塞） */
    @PostMapping("/enterprise/load")
    public String loadEnterpriseDocs(@RequestBody(required = false) Map<String, String> body) {
        String chunkStrategy = body != null ? body.getOrDefault("chunkStrategy", "semantic") : "semantic";
        return ragService.loadEnterpriseDocs(chunkStrategy);
    }
}
