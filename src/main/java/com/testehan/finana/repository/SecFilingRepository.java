package com.testehan.finana.repository;

import com.testehan.finana.model.filing.SecFiling;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SecFilingRepository extends MongoRepository<SecFiling, String> {
    Optional<SecFiling> findBySymbol(String symbol);

    void deleteBySymbol(String symbol);
}
