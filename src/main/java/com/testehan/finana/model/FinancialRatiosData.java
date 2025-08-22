package com.testehan.finana.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "financial_ratios")
public class FinancialRatiosData {
    @Id
    private String id;
    private String symbol;
    private List<FinancialRatiosReport> annualReports;
    private List<FinancialRatiosReport> quarterlyReports;
}
