package com.testehan.finana.model.valuation.growth;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CashFlowYear {

    private int fiscalYear;

    private BigDecimal operatingCashFlow;
    private BigDecimal capitalExpenditures;

    private BigDecimal depreciationAndAmortization;
    private BigDecimal changeInWorkingCapital;
}
