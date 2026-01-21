package com.testehan.finana.repository;

import com.testehan.finana.model.user.UserPortfolio;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPortfolioRepository extends MongoRepository<UserPortfolio, String> {
    Optional<UserPortfolio> findByUserId(String userId);
}