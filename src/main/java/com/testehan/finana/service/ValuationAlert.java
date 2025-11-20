package com.testehan.finana.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValuationAlert {

    private String ticker;
    private String valuationType;
    private String verdict;
    private BigDecimal currentPrice;
    private BigDecimal intrinsicValue;
    private Double upside;
    private Object valuationData;

    public static ValuationAlert fromValuation(String ticker, String valuationType, String verdict, 
                                               BigDecimal currentPrice, BigDecimal intrinsicValue, Object valuationData) {
        Double upside = null;
        if (currentPrice != null && intrinsicValue != null && currentPrice.compareTo(BigDecimal.ZERO) > 0) {
            upside = intrinsicValue.subtract(currentPrice, java.math.MathContext.DECIMAL64)
                    .divide(currentPrice, java.math.MathContext.DECIMAL64)
                    .doubleValue() * 100;
        }

        return ValuationAlert.builder()
                .ticker(ticker)
                .valuationType(valuationType)
                .verdict(verdict)
                .currentPrice(currentPrice)
                .intrinsicValue(intrinsicValue)
                .upside(upside)
                .valuationData(valuationData)
                .build();
    }
}
