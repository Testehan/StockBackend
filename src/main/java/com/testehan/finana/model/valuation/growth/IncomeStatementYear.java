package com.testehan.finana.model.valuation.growth;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class IncomeStatementYear {

    private int fiscalYear;

    private BigDecimal revenue;
    private BigDecimal operatingIncome;
    private BigDecimal pretaxIncome;
    private BigDecimal netIncome;
}
