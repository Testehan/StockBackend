package com.testehan.finana.model.qa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionAnswerResponse {
    private QuestionAnswerStatus status;
    private String answer;
}
