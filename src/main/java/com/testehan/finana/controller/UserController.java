package com.testehan.finana.controller;

import com.testehan.finana.service.LlmService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// TODO this is not used by the client app...just during development ...so it can be removed anytime..
@RestController
public class LlmController {

    private final LlmService llmService;

    public LlmController(LlmService llmService) {
        this.llmService = llmService;
    }

    @PostMapping("/api/llm")
    public String llm(@RequestBody String query) {
        return llmService.callLlm(query);
    }
}
