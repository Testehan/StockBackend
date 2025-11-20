package com.testehan.finana.model.reporting;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "generated_reports")
public class GeneratedReport {
    @Id
    private String symbol;
    private ChecklistReport ferolReport;
    private ChecklistReport oneHundredBaggerReport;
    private Integer totalFerolScore;
    private Integer totalOneHundredBaggerScore;
    private LocalDateTime ferolReportGeneratedAt;
    private LocalDateTime oneHundredBaggerReportGeneratedAt;
}
