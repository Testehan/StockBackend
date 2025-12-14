package com.testehan.finana.repository;

import com.testehan.finana.model.filing.CompanyEarningsTranscripts;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CompanyEarningsTranscriptsRepository extends MongoRepository<CompanyEarningsTranscripts, String> {
    Optional<CompanyEarningsTranscripts> findBySymbol(String symbol);
}
