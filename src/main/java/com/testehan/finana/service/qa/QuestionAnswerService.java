package com.testehan.finana.service.qa;

import com.testehan.finana.model.qa.QuestionAnswerResponse;

public interface QuestionAnswerService {
    QuestionAnswerResponse answerQuestion(String stockId, String questionId);
}
