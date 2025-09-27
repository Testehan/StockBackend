
package com.testehan.finana.repository;

import com.testehan.finana.model.SecFilingsUrls;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SecFilingUrlsRepository extends MongoRepository<SecFilingsUrls, String> {
    void deleteBySymbol(String symbol);
}
