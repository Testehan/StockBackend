package com.testehan.finana.repository;

import com.testehan.finana.model.filing.CompanyEarningsTranscripts;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CompanyEarningsTranscriptsRepository extends MongoRepository<CompanyEarningsTranscripts, String> {
}
