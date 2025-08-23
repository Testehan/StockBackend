package com.testehan.finana.repository;

import com.testehan.finana.model.CompanyEarningsTranscripts;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CompanyEarningsTranscriptsRepository extends MongoRepository<CompanyEarningsTranscripts, String> {
}
