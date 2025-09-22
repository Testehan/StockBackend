package com.testehan.finana.model.llm.responses;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
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

    public FerolMoatAnalysisLlmResponse(int failedOperationScore, String failedExplanation){
        this.networkEffectScore = failedOperationScore;
        this.networkEffectExplanation = failedExplanation;
        this.switchingCostsScore= failedOperationScore;
        this.switchingCostsExplanation= failedExplanation;
        this.durableCostAdvantageScore= failedOperationScore;
        this.durableCostAdvantageExplanation= failedExplanation;
        this.intangiblesScore= failedOperationScore;
        this.intangiblesExplanation= failedExplanation;
        this.counterPositioningScore= failedOperationScore;
        this.counterPositioningExplanation= failedExplanation;
        this.moatDirectionScore= failedOperationScore;
        this.moatDirectionExplanation= failedExplanation;
    }
}
