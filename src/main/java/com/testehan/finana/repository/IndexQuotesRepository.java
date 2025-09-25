package com.testehan.finana.repository;

import com.testehan.finana.model.IndexData;
import com.testehan.finana.model.IndexQuotes;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface IndexQuotesRepository extends MongoRepository<IndexQuotes, String> {

    @Aggregation(pipeline = {
            "{ '$match': { '_id': ?0 } }",
            "{ '$unwind': '$quotes' }",
            "{ '$sort': { 'quotes.date': 1 } }",
            "{ '$limit': 1 }",
            "{ '$replaceRoot': { 'newRoot': '$quotes' } }"
    })
    Optional<IndexData> findFirstQuoteBySymbol(String symbol);

    @Aggregation(pipeline = {
            "{ '$match': { '_id': ?0 } }",
            "{ '$unwind': '$quotes' }",
            "{ '$sort': { 'quotes.date': -1 } }",
            "{ '$limit': 1 }",
            "{ '$replaceRoot': { 'newRoot': '$quotes' } }"
    })
    Optional<IndexData> findLastQuoteBySymbol(String symbol);

    @Aggregation(pipeline = {
            "{ '$match': { '_id': ?0 } }",
            "{ '$unwind': '$quotes' }",
            "{ '$match': { 'quotes.date': ?1 } }",
            "{ '$replaceRoot': { 'newRoot': '$quotes' } }"
    })
    Optional<IndexData> findQuoteBySymbolAndDate(String symbol, String date);
}
