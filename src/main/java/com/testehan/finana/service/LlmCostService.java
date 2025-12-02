package com.testehan.finana.service;

import com.testehan.finana.model.llm.LlmUsage;
import com.testehan.finana.repository.LlmUsageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class LlmCostService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LlmCostService.class);

    private static final String MODEL = "gemini-2.5-pro";

    private static final BigDecimal INPUT_PRICE_SMALL = new BigDecimal("1.25");
    private static final BigDecimal INPUT_PRICE_LARGE = new BigDecimal("2.50");
    private static final BigDecimal OUTPUT_PRICE_SMALL = new BigDecimal("10.00");
    private static final BigDecimal OUTPUT_PRICE_LARGE = new BigDecimal("15.00");
    private static final BigDecimal CACHE_PRICE_SMALL = new BigDecimal("0.125");
    private static final BigDecimal CACHE_PRICE_LARGE = new BigDecimal("0.25");

    private static final int LARGE_TOKEN_THRESHOLD = 200_000;
    private static final int MILLION = 1_000_000;

    private final LlmUsageRepository llmUsageRepository;

    public LlmCostService(LlmUsageRepository llmUsageRepository) {
        this.llmUsageRepository = llmUsageRepository;
    }

    public void logUsage(ChatResponse response, String operationType, String symbol) {
        try {
            int promptTokens = extractPromptTokens(response);
            int completionTokens = extractCompletionTokens(response);
            int cachedTokens = extractCachedTokens(response);
            BigDecimal cost = calculateCost(promptTokens, completionTokens, cachedTokens);

            LlmUsage usage = new LlmUsage();
            usage.setTimestamp(LocalDateTime.now());
            usage.setModel(MODEL);
            usage.setOperationType(operationType);
            usage.setSymbol(symbol);
            usage.setPromptTokens(promptTokens);
            usage.setCompletionTokens(completionTokens);
            usage.setCachedTokens(cachedTokens);
            usage.setTotalCostUsd(cost);
            usage.setSuccess(true);

            llmUsageRepository.save(usage);
            LOGGER.info("LLM usage logged: {} | {} | prompt={} completion={} cached={} cost=${}",
                    operationType, symbol, promptTokens, completionTokens, cachedTokens, cost);
        } catch (Exception e) {
            LOGGER.error("Failed to log LLM usage for {} | {}: {}", operationType, symbol, e.getMessage());
        }
    }

    public void logUsage(String operationType, String symbol, String errorMessage) {
        try {
            LlmUsage usage = new LlmUsage();
            usage.setTimestamp(LocalDateTime.now());
            usage.setModel(MODEL);
            usage.setOperationType(operationType);
            usage.setSymbol(symbol);
            usage.setPromptTokens(0);
            usage.setCompletionTokens(0);
            usage.setCachedTokens(0);
            usage.setTotalCostUsd(BigDecimal.ZERO);
            usage.setSuccess(false);
            usage.setErrorMessage(errorMessage);

            llmUsageRepository.save(usage);
            LOGGER.info("LLM usage logged (failure): {} | {} | error={}", operationType, symbol, errorMessage);
        } catch (Exception e) {
            LOGGER.error("Failed to log failed LLM usage for {} | {}: {}", operationType, symbol, e.getMessage());
        }
    }

    private BigDecimal calculateCost(int promptTokens, int completionTokens, int cachedTokens) {
        BigDecimal promptCost = calculatePromptCost(promptTokens, cachedTokens);
        BigDecimal outputCost = calculateOutputCost(completionTokens);

        return promptCost.add(outputCost).setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePromptCost(int promptTokens, int cachedTokens) {
        BigDecimal inputPrice = promptTokens > LARGE_TOKEN_THRESHOLD ? INPUT_PRICE_LARGE : INPUT_PRICE_SMALL;
        BigDecimal cachePrice = cachedTokens > LARGE_TOKEN_THRESHOLD ? CACHE_PRICE_LARGE : CACHE_PRICE_SMALL;

        int nonCachedTokens = Math.max(0, promptTokens - cachedTokens);
        BigDecimal nonCachedCost = BigDecimal.valueOf(nonCachedTokens)
                .multiply(inputPrice)
                .divide(BigDecimal.valueOf(MILLION), 10, RoundingMode.HALF_UP);
        BigDecimal cachedCost = BigDecimal.valueOf(cachedTokens)
                .multiply(cachePrice)
                .divide(BigDecimal.valueOf(MILLION), 10, RoundingMode.HALF_UP);

        return nonCachedCost.add(cachedCost);
    }

    private BigDecimal calculateOutputCost(int completionTokens) {
        BigDecimal outputPrice = completionTokens > LARGE_TOKEN_THRESHOLD ? OUTPUT_PRICE_LARGE : OUTPUT_PRICE_SMALL;
        return BigDecimal.valueOf(completionTokens)
                .multiply(outputPrice)
                .divide(BigDecimal.valueOf(MILLION), 10, RoundingMode.HALF_UP);
    }

    private int extractPromptTokens(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return 0;
        }
        var usage = response.getMetadata().getUsage();
        if (usage == null) {
            return 0;
        }
        return usage.getPromptTokens();
    }

    private int extractCompletionTokens(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return 0;
        }
        var usage = response.getMetadata().getUsage();
        if (usage == null) {
            return 0;
        }
        return usage.getCompletionTokens();
    }

    private int extractCachedTokens(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return 0;
        }
        var usage = response.getMetadata().getUsage();
        if (usage == null) {
            return 0;
        }
        return ((GoogleGenAiUsage)usage).getCachedContentTokenCount() != null ? ((GoogleGenAiUsage)usage).getCachedContentTokenCount() : 0;
    }
}
