package com.testehan.finana.model.valuation.growth;

import lombok.Data;

@Data
public class BalanceSheetYear {

    private int fiscalYear;

    private double cashAndEquivalents;

    private double shortTermDebt;
    private double longTermDebt;

    private double totalAssets;
    private double totalEquity;
}
