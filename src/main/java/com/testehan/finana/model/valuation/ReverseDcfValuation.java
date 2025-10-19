package com.testehan.finana.model.valuation;

import lombok.Data;

@Data
public class ReverseDcfValuation {
    private String valuationDate;
    private DcfCalculationData dcfCalculationData;
    private ReverseDcfUserInput reverseDcfUserInput;
    private ReverseDcfOutput reverseDcfOutput;
}
