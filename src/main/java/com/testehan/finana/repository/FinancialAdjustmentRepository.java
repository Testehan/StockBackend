package com.testehan.finana.repository;

import com.testehan.finana.model.adjustment.FinancialAdjustment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface FinancialAdjustmentRepository extends MongoRepository<FinancialAdjustment, String> {
    Optional<FinancialAdjustment> findBySymbol(String symbol);
}
