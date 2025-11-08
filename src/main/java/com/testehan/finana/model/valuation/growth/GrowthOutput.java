package com.testehan.finana.model.valuation.growth;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GrowthOutput {
    private BigDecimal intrinsicValuePerShare;
    private String verdict;
}