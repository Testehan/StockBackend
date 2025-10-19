package com.testehan.finana.model.llm.responses;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class TAMScoreExplanationResponse {
    private Integer totalAddressableMarketScore;
    private String totalAddressableMarketExplanation;

    private Integer tamPenetrationRunwayScore;
    private String tamPenetrationRunwayExplanation;

    public TAMScoreExplanationResponse(Integer score, String explanation) {
        this.totalAddressableMarketScore = score;
        this.totalAddressableMarketExplanation = explanation;
        this.tamPenetrationRunwayScore = score;
        this.tamPenetrationRunwayExplanation = explanation;
    }
}
