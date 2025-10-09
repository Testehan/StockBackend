package com.testehan.finana.model.valuation;

import lombok.Data;

@Data
public class DcfValuation {
    private String valuationDate;
    private DcfCalculationData dcfCalculationData;
    private DcfUserInput dcfUserInput;
    private DcfOutput dcfOutput;
}
