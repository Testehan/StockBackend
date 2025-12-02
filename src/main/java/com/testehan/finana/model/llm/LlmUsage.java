package com.testehan.finana.model.llm;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Document(collection = "llm_usage")
public class LlmUsage {
    @Id
    private String id;
    private LocalDateTime timestamp;
    private String model;
    private String operationType;
    private String symbol;
    private int promptTokens;
    private int completionTokens;
    private int cachedTokens;
    private BigDecimal totalCostUsd;
    private boolean success;
    private String errorMessage;
}
