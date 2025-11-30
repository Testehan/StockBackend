package com.testehan.finana.service.qa;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface QuestionAnswerService {
    void answerQuestion(String stockId, String questionId, SseEmitter emitter, boolean regenerate);
}
