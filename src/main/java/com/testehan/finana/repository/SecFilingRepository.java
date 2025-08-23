package com.testehan.finana.repository;

import com.testehan.finana.model.SecFiling;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SecFilingRepository extends MongoRepository<SecFiling, String> {
}
