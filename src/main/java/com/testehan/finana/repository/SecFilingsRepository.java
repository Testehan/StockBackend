
package com.testehan.finana.repository;

import com.testehan.finana.model.SecFilingsUrls;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SecFilingsRepository extends MongoRepository<SecFilingsUrls, String> {
}
