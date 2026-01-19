package com.testehan.finana.model.research;

import com.testehan.deepresearch.model.ResearchTopic;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "research_jobs")
public class ResearchJobRecord {
    @Id
    private String jobId;
    private String stockTicker;
    private ResearchTopic topic;
    private String status;
    private LocalDateTime createdAt;
}