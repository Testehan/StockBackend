package com.testehan.finana.model.llm.responses;

import lombok.Data;

@Data
public class LlmScoreExplanationResponse {
    private Integer score;
    private String explanation;
}
