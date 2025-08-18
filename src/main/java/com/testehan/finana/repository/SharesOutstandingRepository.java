package com.testehan.finana.repository;

import com.testehan.finana.model.SharesOutstandingData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SharesOutstandingRepository extends MongoRepository<SharesOutstandingData, String> {
    Optional<SharesOutstandingData> findBySymbol(String symbol);
}
