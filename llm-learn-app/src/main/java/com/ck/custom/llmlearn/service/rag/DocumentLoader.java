package com.ck.custom.llmlearn.service.rag;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * @author changkong
 * @date 2026/5/10 18:39
 **/
@Service
public class DocumentLoader {

    public String loadMarkdown(String path) {
        try{
            ClassPathResource source = new ClassPathResource(path);
            return new String(source.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }catch (Exception e) {
            throw new RuntimeException("加载文档失败:" + path, e);
        }
    }
}
