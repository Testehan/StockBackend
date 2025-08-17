package com.testehan.finana.repository;

import com.testehan.finana.model.BalanceSheetData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BalanceSheetRepository extends MongoRepository<BalanceSheetData, String> {
    Optional<BalanceSheetData> findBySymbol(String symbol);
}
