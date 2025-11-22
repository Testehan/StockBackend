package com.testehan.finana.service;

import com.testehan.finana.model.adjustment.FinancialAdjustment;

public interface AdjustmentService {
    FinancialAdjustment getFinancialAdjustments(String symbol);
    void deleteFinancialAdjustmentBySymbol(String symbol);
}
