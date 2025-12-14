package com.testehan.finana.controller;

import com.testehan.finana.model.qa.QuestionConstants;
import com.testehan.finana.model.qa.Question;
import com.testehan.finana.model.qa.StockSentiment;
import com.testehan.finana.service.qa.QuestionAnswerService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/stocks/questions")
public class QuestionsController {

    private final QuestionAnswerService questionAnswerService;
    private final ExecutorService executorService;

    public QuestionsController(QuestionAnswerService questionAnswerService, @Qualifier("checklistExecutor") ExecutorService executorService) {
        this.questionAnswerService = questionAnswerService;
        this.executorService = executorService;
    }

    @GetMapping("/business")
    public List<Question> getBusinessAnalysisQuestions() {
        return QuestionConstants.BUSINESS_ANALYSIS_QUESTIONS;
    }

    @GetMapping("/transcript")
    public List<Question> getEarningTranscriptQuestions() {
        return QuestionConstants.EARNINGS_TRANSCRIPT_QUESTIONS;
    }

    @GetMapping("/guru/{guruName}")
    public List<Question> getGuruQuestions(@PathVariable String guruName) {
        return QuestionConstants.getGuruQuestions(guruName);
    }

    @GetMapping("/answer")
    public SseEmitter answerQuestion(@RequestParam String stockId, @RequestParam String questionId, 
            @RequestParam(required = false, defaultValue = "false") boolean regenerate,
            @RequestParam(required = false) String additionalInformation) {
        
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        executorService.execute(() -> {
            try {
                questionAnswerService.answerQuestion(stockId.toUpperCase(), questionId, additionalInformation, emitter, regenerate);
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }

    @GetMapping("/sentiment")
    public StockSentiment getSentiment(@RequestParam String stockId, @RequestParam(required = false, defaultValue = "false") boolean regenerate) {
        return questionAnswerService.getSentiment(stockId.toUpperCase(), regenerate);
    }

    @PostMapping
    public ResponseEntity<String> queryStock(@RequestBody String question) {
        String answer = questionAnswerService.queryStock(question);
        return ResponseEntity.ok(answer);
    }
}
