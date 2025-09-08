package com.testehan.finana.repository;

import com.testehan.finana.model.CashFlowData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CashFlowRepository extends MongoRepository<CashFlowData, String> {
    Optional<CashFlowData> findBySymbol(String symbol);

    void deleteBySymbol(String symbol);
}
