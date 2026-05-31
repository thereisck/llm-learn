package com.ck.custom.llmlearn.domain.rag;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 企业级RAG问答响应 —— 在标准RagQueryResponse基础上新增：
 * - confidence: 系统对答案的置信度（基于检索最高分）
 * - outOfDomain: 是否判定为知识库外问题
 * - searchMode: 实际使用的检索模式
 * - compressMode: 实际使用的压缩模式
 */
@Data
@AllArgsConstructor
public class EnterpriseRagResponse {
    private String question;
    private String answer;
    private List<SearchResult> sources;
    private double confidence;
    private boolean outOfDomain;
    private String searchMode;
    private String compressMode;
}