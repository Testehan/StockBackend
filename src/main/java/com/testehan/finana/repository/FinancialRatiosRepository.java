package com.testehan.finana.repository;

import com.testehan.finana.model.FinancialRatios;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FinancialRatiosRepository extends MongoRepository<FinancialRatios, String> {
    List<FinancialRatios> findBySymbol(String symbol);
}
