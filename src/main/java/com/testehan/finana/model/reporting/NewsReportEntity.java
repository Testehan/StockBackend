package com.testehan.finana.model.reporting;

import com.testehan.deepresearch.model.NewsReport;
import com.testehan.deepresearch.model.ResearchTopic;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "news_reports")
public class NewsReportEntity {
    @Id
    private String jobId;
    private String stockTicker;
    private ResearchTopic topic;
    private NewsReport report;
    private String status;
    private LocalDateTime createdAt;
}