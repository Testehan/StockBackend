package com.testehan.finana.model.finstatement;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "balance_sheets")
public class BalanceSheetData {
    @Id
    private String id;

    @Indexed(unique = true)
    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("annualReports")
    private List<BalanceSheetReport> annualReports;

    @JsonProperty("quarterlyReports")
    private List<BalanceSheetReport> quarterlyReports;

    private LocalDateTime lastUpdated;
}
