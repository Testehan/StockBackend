package com.testehan.finana.repository;

import com.testehan.finana.model.EarningsEstimate;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface EarningsEstimatesRepository extends MongoRepository<EarningsEstimate, String> {
    Optional<EarningsEstimate> findBySymbol(String symbol);

    void deleteBySymbol(String upperCaseSymbol);
}
