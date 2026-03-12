package com.testehan.finana.controller;

import com.testehan.finana.model.llm.LlmUsage;
import com.testehan.finana.repository.LlmUsageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/llm-usage")
public class LlmUsageController {

    private final LlmUsageRepository llmUsageRepository;

    public LlmUsageController(LlmUsageRepository llmUsageRepository) {
        this.llmUsageRepository = llmUsageRepository;
    }

    @GetMapping
    public Page<LlmUsage> getUsage(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        String userEmail = extractUserEmail();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));

        if (symbol != null && operationType != null && fromDate != null && toDate != null) {
            return llmUsageRepository.findByUserEmailAndSymbolAndOperationTypeAndTimestampBetween(userEmail, symbol, operationType, fromDate, toDate, pageRequest);
        } else if (symbol != null && operationType != null) {
            return llmUsageRepository.findByUserEmailAndSymbolAndOperationType(userEmail, symbol, operationType, pageRequest);
        } else if (symbol != null && fromDate != null && toDate != null) {
            return llmUsageRepository.findByUserEmailAndSymbolAndTimestampBetween(userEmail, symbol, fromDate, toDate, pageRequest);
        } else if (symbol != null) {
            return llmUsageRepository.findByUserEmailAndSymbol(userEmail, symbol, pageRequest);
        } else if (operationType != null) {
            return llmUsageRepository.findByUserEmailAndOperationType(userEmail, operationType, pageRequest);
        } else if (fromDate != null && toDate != null) {
            return llmUsageRepository.findByUserEmailAndTimestampBetween(userEmail, fromDate, toDate, pageRequest);
        }

        return llmUsageRepository.findByUserEmail(userEmail, pageRequest);
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {

        String userEmail = extractUserEmail();
        LocalDateTime from = fromDate != null ? fromDate : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime to = toDate != null ? toDate : LocalDateTime.now();

        List<LlmUsage> usages;
        if (symbol != null) {
            usages = llmUsageRepository.findByUserEmailAndSymbolAndTimestampBetween(userEmail, symbol, from, to);
        } else if (operationType != null) {
            usages = llmUsageRepository.findByUserEmailAndOperationType(userEmail, operationType);
        } else {
            usages = llmUsageRepository.findByUserEmailAndTimestampBetween(userEmail, from, to);
        }

        return calculateSummary(usages);
    }

    private String extractUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String email = jwtAuth.getToken().getClaimAsString("email");
            if (email != null) {
                return email;
            }
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated user or email claim missing from JWT.");
    }

    private Map<String, Object> calculateSummary(List<LlmUsage> usages) {
        Map<String, Object> summary = new HashMap<>();

        long totalCalls = usages.size();
        long successfulCalls = usages.stream().filter(LlmUsage::isSuccess).count();
        long failedCalls = totalCalls - successfulCalls;

        int totalPromptTokens = usages.stream().mapToInt(LlmUsage::getPromptTokens).sum();
        int totalCompletionTokens = usages.stream().mapToInt(LlmUsage::getCompletionTokens).sum();
        int totalCachedTokens = usages.stream().mapToInt(LlmUsage::getCachedTokens).sum();
        BigDecimal totalCost = usages.stream()
                .map(LlmUsage::getTotalCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Integer> callsByOperation = new HashMap<>();
        Map<String, Integer> tokensByOperation = new HashMap<>();
        Map<String, BigDecimal> costByOperation = new HashMap<>();

        for (LlmUsage usage : usages) {
            String op = usage.getOperationType();
            callsByOperation.merge(op, 1, Integer::sum);
            tokensByOperation.merge(op, usage.getPromptTokens() + usage.getCompletionTokens(), Integer::sum);
            costByOperation.merge(op, usage.getTotalCostUsd(), BigDecimal::add);
        }

        summary.put("totalCalls", totalCalls);
        summary.put("successfulCalls", successfulCalls);
        summary.put("failedCalls", failedCalls);
        summary.put("totalPromptTokens", totalPromptTokens);
        summary.put("totalCompletionTokens", totalCompletionTokens);
        summary.put("totalCachedTokens", totalCachedTokens);
        summary.put("totalCostUsd", totalCost);
        summary.put("callsByOperation", callsByOperation);
        summary.put("tokensByOperation", tokensByOperation);
        summary.put("costByOperation", costByOperation);

        return summary;
    }
}
