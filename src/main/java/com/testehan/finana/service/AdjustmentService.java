package com.testehan.finana.service;

import com.testehan.finana.model.adjustment.FinancialAdjustment;
import reactor.core.publisher.Mono;

public interface AdjustmentService {
    Mono<FinancialAdjustment> getFinancialAdjustments(String symbol);
    void deleteFinancialAdjustmentBySymbol(String symbol);
}
