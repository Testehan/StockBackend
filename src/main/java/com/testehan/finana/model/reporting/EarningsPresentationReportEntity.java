package com.testehan.finana.model.reporting;

import com.testehan.deepresearch.model.EarningsPresentationReport;
import com.testehan.deepresearch.model.ResearchTopic;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "earnings_presentations")
public class EarningsPresentationReportEntity {
    @Id
    private String jobId;
    private String stockTicker;
    private ResearchTopic topic;
    private EarningsPresentationReport report;
    private String status;
    private LocalDateTime createdAt;
}