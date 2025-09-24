package com.testehan.finana.repository;

import com.testehan.finana.model.RevenueGeographicSegmentationData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RevenueGeographicSegmentationRepository extends MongoRepository<RevenueGeographicSegmentationData, String> {
    Optional<RevenueGeographicSegmentationData> findBySymbol(String symbol);
    void deleteBySymbol(String symbol);
}
