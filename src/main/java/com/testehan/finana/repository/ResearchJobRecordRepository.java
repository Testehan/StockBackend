package com.testehan.finana.repository;

import com.testehan.finana.model.research.ResearchJobRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResearchJobRecordRepository extends MongoRepository<ResearchJobRecord, String> {
    List<ResearchJobRecord> findAllByStatus(String status);
    List<ResearchJobRecord> findByStockTicker(String stockTicker);
}