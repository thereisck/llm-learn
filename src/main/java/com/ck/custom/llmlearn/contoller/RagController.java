package com.ck.custom.llmlearn.contoller;

import com.ck.custom.llmlearn.domain.rag.RagQueryRequest;
import com.ck.custom.llmlearn.domain.rag.RagQueryResponse;
import com.ck.custom.llmlearn.service.rag.RagService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author changkong
 * @date 2026/5/10 21:20
 **/
@RestController
@RequestMapping("/rag")
public class RagController {

    @Resource
    private RagService ragService;

    @PostMapping("/query")
    public RagQueryResponse query(@RequestBody RagQueryRequest request) {
        return ragService.query(request.getQuestion(), request.getThreshold());
    }
}
