package com.testehan.finana.repository;

import com.testehan.finana.model.qa.QuestionAnswer;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface QuestionAnswerRepository extends MongoRepository<QuestionAnswer, String> {
    Optional<QuestionAnswer> findByStockIdAndQuestionIdAndPromptVersionAndModel(
            String stockId, String questionId, String promptVersion, String model);
}
