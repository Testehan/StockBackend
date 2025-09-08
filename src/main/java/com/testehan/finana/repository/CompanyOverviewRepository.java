package com.testehan.finana.repository;

import com.testehan.finana.model.CompanyOverview;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyOverviewRepository extends MongoRepository<CompanyOverview, String> {
    Optional<CompanyOverview> findBySymbol(String symbol);

    void deleteBySymbol(String symbol);
}
