package com.testehan.finana.repository;

import com.testehan.finana.model.IncomeStatementData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IncomeStatementRepository extends MongoRepository<IncomeStatementData, String> {
    Optional<IncomeStatementData> findBySymbol(String symbol);
}
