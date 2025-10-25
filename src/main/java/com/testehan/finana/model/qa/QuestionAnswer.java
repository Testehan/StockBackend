package com.testehan.finana.model.qa;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document("stock_questions_answers")
@CompoundIndex(name = "stock_question_prompt_model_idx", def = "{'stockId' : 1, 'questionId': 1, 'promptVersion': 1, 'model': 1}", unique = true)
public class QuestionAnswer {
    @Id
    private String id;
    private String stockId;
    private String questionId;
    private QuestionAnswerStatus status;
    private String answer;

    private String model;
    private String promptVersion;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
