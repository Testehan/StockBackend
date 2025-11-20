package com.testehan.finana.model.ratio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FmpRatiosTtm {
    private String symbol;
    private String date;
    private Double priceToEarningsRatioTTM;
    private Double priceToEarningsGrowthRatioTTM;
    private Double forwardPriceToEarningsGrowthRatioTTM;
    private Double priceToBookRatioTTM;
    private Double priceToSalesRatioTTM;
    private Double priceToFreeCashFlowRatioTTM;
    private Double priceToOperatingCashFlowRatioTTM;
    private Double priceToFairValueTTM;
    private Double enterpriseValueMultipleTTM;
}
