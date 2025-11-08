package com.testehan.finana.model.valuation.dcf;

import lombok.Data;

@Data
public class DcfValuation {
    private String valuationDate;
    private DcfCalculationData dcfCalculationData;
    private DcfUserInput dcfUserInput;
    private DcfOutput dcfOutput;
}
