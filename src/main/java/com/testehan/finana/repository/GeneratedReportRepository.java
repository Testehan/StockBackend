package com.testehan.finana.repository;

import com.testehan.finana.model.reporting.GeneratedReport;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GeneratedReportRepository extends MongoRepository<GeneratedReport, String> {
    Optional<GeneratedReport> findBySymbol(String symbol);

    void deleteBySymbol(String symbol);

    List<GeneratedReport> findBySymbolIn(List<String> symbols);
}
