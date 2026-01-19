package com.testehan.finana.repository;

import com.testehan.finana.model.reporting.NewsReportEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NewsReportRepository extends MongoRepository<NewsReportEntity, String> {
    List<NewsReportEntity> findByStockTickerAndCreatedAtAfterOrderByCreatedAtDesc(String stockTicker, LocalDateTime date);
    Optional<NewsReportEntity> findFirstByStockTickerAndCreatedAtAfterOrderByCreatedAtDesc(String stockTicker, LocalDateTime date);
    List<NewsReportEntity> findAllByStatus(String status);
    List<NewsReportEntity> findByCreatedAtBefore(LocalDateTime date);
}