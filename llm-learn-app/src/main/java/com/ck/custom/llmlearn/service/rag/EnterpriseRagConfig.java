package com.ck.custom.llmlearn.service.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 企业级RAG最佳配置 —— 基于Week4前6天实验数据固化
 *
 * 实验结论来源：
 * - Day1: threshold=0.50 最优（0.35太宽，0.65误伤，0.80召回失明）
 * - Day2: Hybrid Search(vector+BM25+RRF) 召回覆盖面最广
 * - Day3: Rerank(bge-reranker-v2-m3) 精排提升显著
 * - Day4: SemanticChunker(THRESHOLD=0.5) chunk质量最优
 * - Day5: 上下文压缩(summary模式) 减少噪声
 * - Day6: multi_route≈hybrid_rerank，小知识库第三路无额外增益
 * - Day7新增: 知识库外检测（低于拒绝阈值时拒绝回答）
 */
@Data
@Component
@ConfigurationProperties(prefix = "enterprise.rag")
public class EnterpriseRagConfig {

    /** 切分策略: semantic-threshold（Day4结论） */
    private String chunkStrategy = "semantic";

    /** SemanticChunker断点策略 */
    private String semanticStrategy = "THRESHOLD";

    /** SemanticChunker参数值 */
    private double semanticParam = 0.5;

    /** 检索模式: hybrid_rerank（Day2+3结论） */
    private String searchMode = "hybrid_rerank";

    /** 相似度阈值: 0.50（Day1结论） */
    private double threshold = 0.5;

    /** 每路召回数量 */
    private int topK = 5;

    /** Rerank候选数量 */
    private int rerankCandidateK = 20;

    /** Rerank精排保留数量 */
    private int rerankTopN = 5;

    /** 是否启用上下文压缩（Day5结论） */
    private boolean enableCompression = true;

    /** 压缩模式: summary / extractive */
    private String compressMode = "summary";

    /** 是否启用精确匹配加权（Day6结论） */
    private boolean exactMatchBoost = true;

    /** 是否启用知识库外检测（Day7新增） */
    private boolean outOfDomainDetection = true;

    /** 知识库外拒绝阈值：检索结果最高score低于此值时拒绝回答 */
    private double outOfDomainThreshold = 0.4;

    /** 知识库外拒绝提示语 */
    private String outOfDomainMessage = "该问题不在当前知识库范围内，请咨询相关部门获取准确信息。";

    /** 企业文档目录（classpath路径） */
    private String docDir = "docs/enterprise";
}