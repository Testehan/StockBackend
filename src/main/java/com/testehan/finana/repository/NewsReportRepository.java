package com.testehan.finana.repository;

import com.testehan.finana.model.reporting.NewsReport;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NewsReportRepository extends MongoRepository<NewsReport, String> {
    List<NewsReport> findByStockTickerAndCreatedAtAfterOrderByCreatedAtDesc(String stockTicker, LocalDateTime date);
    Optional<NewsReport> findFirstByStockTickerAndCreatedAtAfterOrderByCreatedAtDesc(String stockTicker, LocalDateTime date);
    List<NewsReport> findAllByStatus(String status);
    List<NewsReport> findByCreatedAtBefore(LocalDateTime date);
}