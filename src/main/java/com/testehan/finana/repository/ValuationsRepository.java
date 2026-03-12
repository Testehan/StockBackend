package com.testehan.finana.repository;

import com.testehan.finana.model.valuation.Valuations;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ValuationsRepository extends MongoRepository<Valuations, String> {
    Optional<Valuations> findByTickerAndUserEmail(String ticker, String userEmail);
}
