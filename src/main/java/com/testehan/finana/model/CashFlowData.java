package com.testehan.finana.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "cash_flows")
public class CashFlowData {
    @Id
    private String id;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("annualReports")
    private List<CashFlowReport> annualReports;

    @JsonProperty("quarterlyReports")
    private List<CashFlowReport> quarterlyReports;
}
