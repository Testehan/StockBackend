package com.testehan.finana.repository;

import com.testehan.finana.model.user.UserStock;
import com.testehan.finana.model.user.UserStockStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserStockRepository extends MongoRepository<UserStock, String> {
    List<UserStock> findByUserId(String userId);
    Optional<UserStock> findByUserIdAndStockId(String userId, String stockId);
    List<UserStock> findByStatus(UserStockStatus status);
}
