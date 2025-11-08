package com.testehan.finana.model.valuation.dcf;

import lombok.Data;

@Data
public class ReverseDcfUserInput {
    private Double discountRate;
    private Double perpetualGrowthRate;
    private Integer projectionYears;
    private String userComments;
}
