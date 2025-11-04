package com.testehan.finana.controller;

import com.testehan.finana.model.qa.BusinessAnalysisQuestions;
import com.testehan.finana.model.qa.Question;
import com.testehan.finana.service.qa.QuestionAnswerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/stocks/questions")
public class QuestionsController {

    private final QuestionAnswerService questionAnswerService;
    private final ExecutorService executorService;

    @Autowired
    public QuestionsController(QuestionAnswerService questionAnswerService, @Qualifier("checklistExecutor") ExecutorService executorService) {
        this.questionAnswerService = questionAnswerService;
        this.executorService = executorService;
    }

    @GetMapping()
    public List<Question> getQuestions() {
        return BusinessAnalysisQuestions.QUESTIONS;
    }

    @GetMapping("/answer")
    public SseEmitter answerQuestion(@RequestParam String stockId, @RequestParam String questionId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        executorService.execute(() -> {
            try {
                questionAnswerService.answerQuestion(stockId, questionId, emitter);
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }
}
