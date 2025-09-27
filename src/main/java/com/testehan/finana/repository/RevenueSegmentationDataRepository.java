package com.testehan.finana.repository;

import com.testehan.finana.model.RevenueSegmentationData;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RevenueSegmentationDataRepository extends MongoRepository<RevenueSegmentationData, String> {
    Optional<RevenueSegmentationData> findBySymbol(String symbol);
    void deleteBySymbol(String symbol);
}
