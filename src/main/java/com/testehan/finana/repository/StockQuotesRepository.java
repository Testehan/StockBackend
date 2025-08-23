
package com.testehan.finana.repository;

import com.testehan.finana.model.StockQuotes;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockQuotesRepository extends MongoRepository<StockQuotes, String> {
    Optional<StockQuotes> findBySymbol(String symbol);
}
