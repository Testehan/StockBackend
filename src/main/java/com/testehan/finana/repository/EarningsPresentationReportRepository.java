package com.testehan.finana.repository;

import com.testehan.finana.model.reporting.EarningsPresentationReport;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EarningsPresentationReportRepository extends MongoRepository<EarningsPresentationReport, String> {
    List<EarningsPresentationReport> findByStockTickerOrderByCreatedAtDesc(String stockTicker);
    Optional<EarningsPresentationReport> findFirstByStockTickerOrderByCreatedAtDesc(String stockTicker);
    List<EarningsPresentationReport> findAllByStatus(String status);
}