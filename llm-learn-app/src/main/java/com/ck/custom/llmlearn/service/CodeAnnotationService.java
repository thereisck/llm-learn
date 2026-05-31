package com.ck.custom.llmlearn.service;

import com.ck.custom.llmlearn.domain.CodeAnnotateRequest;
import com.ck.custom.llmlearn.domain.CodeAnnotateResponse;

public interface CodeAnnotationService {
    CodeAnnotateResponse annotate(CodeAnnotateRequest request);
}
