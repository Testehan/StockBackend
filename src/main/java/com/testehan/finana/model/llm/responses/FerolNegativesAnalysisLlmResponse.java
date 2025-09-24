package com.testehan.finana.model.llm.responses;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class FerolNegativesAnalysisLlmResponse {
    private Integer accountingIrregularitiesScore;
    private String accountingIrregularitiesExplanation;

    private Integer customerConcentrationScore;
    private String customerConcentrationExplanation;

    private Integer industryDisruptionScore;
    private String industryDisruptionExplanation;

    private Integer outsideForcesScore;
    private String outsideForcesExplanation;

    private Integer binaryEventScore;
    private String binaryEventExplanation;

    private Integer growthByAcquisitionScore;
    private String growthByAcquisitionExplanation;

    private Integer complicatedFinancialsScore;
    private String complicatedFinancialsExplanation;

    private Integer antitrustConcernsScore;
    private String antitrustConcernsExplanation;

    public FerolNegativesAnalysisLlmResponse(int failedOperationScore, String failedExplanation){
        this.accountingIrregularitiesScore = failedOperationScore;
        this.accountingIrregularitiesExplanation = failedExplanation;

        this.customerConcentrationScore = failedOperationScore;
        this.customerConcentrationExplanation = failedExplanation;

        this.industryDisruptionScore = failedOperationScore;
        this.industryDisruptionExplanation = failedExplanation;

        this.outsideForcesScore = failedOperationScore;
        this.outsideForcesExplanation = failedExplanation;

        this.binaryEventScore = failedOperationScore;
        this.binaryEventExplanation = failedExplanation;

        this.growthByAcquisitionScore = failedOperationScore;
        this.growthByAcquisitionExplanation = failedExplanation;

        this.complicatedFinancialsScore = failedOperationScore;
        this.complicatedFinancialsExplanation = failedExplanation;

        this.antitrustConcernsScore = failedOperationScore;
        this.antitrustConcernsExplanation = failedExplanation;
    }
}
