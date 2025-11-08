package com.testehan.finana.model.valuation.growth;

import lombok.Data;

@Data
public class CashFlowYear {

    private int fiscalYear;

    private double operatingCashFlow;
    private double capitalExpenditures;

    private double depreciationAndAmortization;
    private double changeInWorkingCapital;
}
