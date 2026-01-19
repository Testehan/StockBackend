package com.testehan.finana.repository;

import com.testehan.finana.model.reporting.EarningsPresentationReportEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EarningsPresentationReportRepository extends MongoRepository<EarningsPresentationReportEntity, String> {
    List<EarningsPresentationReportEntity> findByStockTickerOrderByCreatedAtDesc(String stockTicker);
    Optional<EarningsPresentationReportEntity> findFirstByStockTickerOrderByCreatedAtDesc(String stockTicker);
    List<EarningsPresentationReportEntity> findAllByStatus(String status);
}