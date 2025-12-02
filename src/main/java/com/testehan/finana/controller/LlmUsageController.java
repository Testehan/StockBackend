package com.testehan.finana.controller;

import com.testehan.finana.model.llm.LlmUsage;
import com.testehan.finana.repository.LlmUsageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));

        if (symbol != null && fromDate != null && toDate != null) {
            return llmUsageRepository.findBySymbolAndTimestampBetween(
                    symbol, fromDate.atStartOfDay(), toDate.atTime(LocalTime.MAX), pageRequest);
        } else if (symbol != null) {
            return llmUsageRepository.findBySymbol(symbol, pageRequest);
        } else if (operationType != null) {
            return llmUsageRepository.findByOperationType(operationType, pageRequest);
        } else if (fromDate != null && toDate != null) {
            return llmUsageRepository.findByTimestampBetween(
                    fromDate.atStartOfDay(), toDate.atTime(LocalTime.MAX), pageRequest);
        }

        return llmUsageRepository.findAll(pageRequest);
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime to = toDate != null ? toDate.atTime(LocalTime.MAX) : LocalDateTime.now();

        List<LlmUsage> usages;
        if (symbol != null) {
            Page<LlmUsage> page = llmUsageRepository.findBySymbolAndTimestampBetween(symbol, from, to, PageRequest.of(0, Integer.MAX_VALUE));
            usages = page.getContent();
        } else if (operationType != null) {
            Page<LlmUsage> page = llmUsageRepository.findByOperationType(operationType, PageRequest.of(0, Integer.MAX_VALUE));
            usages = page.getContent();
        } else {
            usages = llmUsageRepository.findByTimestampBetween(from, to);
        }

        return calculateSummary(usages);
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
