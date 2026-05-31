package com.ck.custom.llmlearn.contoller;

import com.ck.custom.llmlearn.domain.CodeAnnotateRequest;
import com.ck.custom.llmlearn.domain.CodeAnnotateResponse;
import com.ck.custom.llmlearn.service.CodeAnnotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CodeAnnotateController {

    private final CodeAnnotationService codeAnnotationService;

    @PostMapping("/annotate")
    public CodeAnnotateResponse annotate(@RequestBody CodeAnnotateRequest request) {
        return codeAnnotationService.annotate(request);
    }
}
