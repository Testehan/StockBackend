package com.testehan.finana.repository;

import com.testehan.finana.model.llm.LlmUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LlmUsageRepository extends MongoRepository<LlmUsage, String> {
    Page<LlmUsage> findBySymbol(String symbol, Pageable pageable);
    Page<LlmUsage> findByOperationType(String operationType, Pageable pageable);
    Page<LlmUsage> findByTimestampBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);
    Page<LlmUsage> findBySymbolAndTimestampBetween(String symbol, LocalDateTime from, LocalDateTime to, Pageable pageable);
    Page<LlmUsage> findBySymbolAndOperationType(String symbol, String operationType, Pageable pageable);
    Page<LlmUsage> findBySymbolAndOperationTypeAndTimestampBetween(String symbol, String operationType, LocalDateTime from, LocalDateTime to, Pageable pageable);
    List<LlmUsage> findByTimestampBetween(LocalDateTime from, LocalDateTime to);
}
