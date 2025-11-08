package com.testehan.finana.model.valuation.growth;

import lombok.Data;

@Data
public class IncomeStatementYear {

    private int fiscalYear;

    private double revenue;
    private double operatingIncome;
    private double pretaxIncome;
    private double netIncome;
}
