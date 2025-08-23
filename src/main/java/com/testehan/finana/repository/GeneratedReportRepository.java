package com.testehan.finana.repository;

import com.testehan.finana.model.GeneratedReport;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GeneratedReportRepository extends MongoRepository<GeneratedReport, String> {
    Optional<GeneratedReport> findBySymbol(String symbol);
}
