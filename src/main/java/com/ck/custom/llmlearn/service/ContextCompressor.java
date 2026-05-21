package com.ck.custom.llmlearn.service;

import java.util.List;

/**
 * @author changkong
 * @date 2026/5/19 22:43
 **/
public interface ContextCompressor {
    /**
     * 压缩上下文
     * @param chunks 检索到的原始chunk列表
     * @param query 用户问题
     * @return 压缩后的文本
     */
    String compress(List<String> chunks, String query);
}
