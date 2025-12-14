package com.testehan.finana.service.qa;

import com.testehan.finana.model.qa.StockSentiment;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface QuestionAnswerService {
    void answerQuestion(String stockId, String questionId, String additionalInformation, SseEmitter emitter, boolean regenerate);
    StockSentiment getSentiment(String stockId, boolean regenerate);
    String queryStock(String question);
}
