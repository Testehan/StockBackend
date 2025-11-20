package com.testehan.finana.model.adjustment;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "financial_adjustments")
public class FinancialAdjustment {
    @Id
    private String id;
    private String symbol;
    private LocalDateTime lastUpdated;

    private List<FinancialAdjustmentReport> annualAdjustments;
}