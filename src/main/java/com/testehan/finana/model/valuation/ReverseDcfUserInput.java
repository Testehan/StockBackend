package com.testehan.finana.model.valuation;

import lombok.Data;

@Data
public class ReverseDcfUserInput {
    private Double discountRate;
    private Double perpetualGrowthRate;
    private Integer projectionYears;
    private String userComments;
}
