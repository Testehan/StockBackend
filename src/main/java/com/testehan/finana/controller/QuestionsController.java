package com.testehan.finana.controller;

import com.testehan.finana.model.qa.BusinessAnalysisQuestions;
import com.testehan.finana.model.qa.Question;
import com.testehan.finana.model.qa.QuestionAnswerResponse;
import com.testehan.finana.service.qa.QuestionAnswerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/stocks/questions")
public class QuestionsController {

    private final QuestionAnswerService questionAnswerService;

    @Autowired
    public QuestionsController(QuestionAnswerService questionAnswerService) {
        this.questionAnswerService = questionAnswerService;
    }

    @GetMapping()
    public List<Question> getQuestions() {
        return BusinessAnalysisQuestions.QUESTIONS;
    }

    @PostMapping("/answer")
    public QuestionAnswerResponse answerQuestion(@RequestParam String stockId, @RequestParam String questionId) {
        return questionAnswerService.answerQuestion(stockId, questionId);
    }
}
