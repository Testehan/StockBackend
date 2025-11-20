package com.testehan.finana.model.ratio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FmpRatios {
    private String symbol;
    private String date;
    private String fiscalYear;
    private Double priceToEarningsRatio;
    private Double priceToEarningsGrowthRatio;
    private Double forwardPriceToEarningsGrowthRatio;
    private Double priceToBookRatio;
    private Double priceToSalesRatio;
    private Double priceToFreeCashFlowRatio;
    private Double priceToOperatingCashFlowRatio;
    private Double priceToFairValue;
    private Double enterpriseValueMultiple;
}
