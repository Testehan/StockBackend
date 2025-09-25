
package com.testehan.finana.repository;

import com.testehan.finana.model.GlobalQuote;
import com.testehan.finana.model.StockQuotes;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockQuotesRepository extends MongoRepository<StockQuotes, String> {
    Optional<StockQuotes> findBySymbol(String symbol);

    void deleteBySymbol(String symbol);

    @Aggregation(pipeline = {
            "{ '$match': { 'symbol' : ?0 } }",
            "{ '$unwind': '$quotes' }",
            "{ '$sort': { 'quotes.date': 1 } }",
            "{ '$limit': 1 }",
            "{ '$replaceRoot': { 'newRoot': '$quotes' } }"
    })
    Optional<GlobalQuote> findFirstQuoteBySymbol(String symbol);

    @Aggregation(pipeline = {
            "{ '$match': { 'symbol' : ?0 } }",
            "{ '$unwind': '$quotes' }",
            "{ '$sort': { 'quotes.date': -1 } }",
            "{ '$limit': 1 }",
            "{ '$replaceRoot': { 'newRoot': '$quotes' } }"
    })
    Optional<GlobalQuote> findLastQuoteBySymbol(String symbol);

    @Aggregation(pipeline = {
            "{ '$match': { '_id': ?0 } }",
            "{ '$unwind': '$quotes' }",
            "{ '$match': { 'quotes.date': ?1 } }",
            "{ '$replaceRoot': { 'newRoot': '$quotes' } }"
    })
    Optional<GlobalQuote> findQuoteBySymbolAndDate(String symbol, String date);
}
