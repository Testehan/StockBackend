package com.testehan.finana.repository;

import com.testehan.finana.model.FinancialRatiosData;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface FinancialRatiosRepository extends MongoRepository<FinancialRatiosData, String> {
    Optional<FinancialRatiosData> findBySymbol(String symbol);

    void deleteBySymbol(String symbol);
}
