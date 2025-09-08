
package com.testehan.finana.repository;

import com.testehan.finana.model.EarningsHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EarningsHistoryRepository extends MongoRepository<EarningsHistory, String> {
    Optional<EarningsHistory> findBySymbol(String symbol);

    void deleteBySymbol(String symbol);
}
