package com.testehan.finana.model.valuation.growth;

import lombok.Data;

@Data
public class GrowthValuation {
    private String valuationDate;
    private GrowthValuationData growthValuationData;
    private GrowthUserInput growthUserInput;
    private GrowthOutput growthOutput;
}
