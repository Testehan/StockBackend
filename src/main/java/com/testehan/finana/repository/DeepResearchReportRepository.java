package com.testehan.finana.repository;

import com.testehan.finana.model.reporting.DeepResearchReport;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeepResearchReportRepository extends MongoRepository<DeepResearchReport, String> {
    List<DeepResearchReport> findByStockTickerAndCreatedAtAfterOrderByCreatedAtDesc(String stockTicker, LocalDateTime date);
    Optional<DeepResearchReport> findFirstByStockTickerAndCreatedAtAfterOrderByCreatedAtDesc(String stockTicker, LocalDateTime date);
    List<DeepResearchReport> findAllByStatus(String status);
    List<DeepResearchReport> findByCreatedAtBefore(LocalDateTime date);
}
