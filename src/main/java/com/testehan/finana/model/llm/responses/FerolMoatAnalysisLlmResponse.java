package com.testehan.finana.model.llm.responses;

import lombok.Data;

@Data
public class FerolMoatAnalysisLlmResponse {
    private Integer networkEffectScore;
    private String networkEffectExplanation;

    private Integer switchingCostsScore;
    private String switchingCostsExplanation;

    private Integer durableCostAdvantageScore;
    private String durableCostAdvantageExplanation;

    private Integer intangiblesScore;
    private String intangiblesExplanation;

    private Integer counterPositioningScore;
    private String counterPositioningExplanation;

    private Integer moatDirectionScore;
    private String moatDirectionExplanation;
}
