package com.testehan.finana.model.reporting;

import com.testehan.deepresearch.model.ResearchReport;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "deep_research_reports")
public class DeepResearchReport {
    @Id
    private String jobId;
    private String stockTicker;
    private String topic;
    private ResearchReport report;
    private String status;
    private LocalDateTime createdAt;
}
