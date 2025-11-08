package com.testehan.finana.model.valuation.growth;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BalanceSheetYear {

    private int fiscalYear;

    private BigDecimal cashAndEquivalents;

    private BigDecimal shortTermDebt;
    private BigDecimal longTermDebt;

    private BigDecimal totalAssets;
    private BigDecimal totalEquity;
}

