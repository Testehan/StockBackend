package com.testehan.finana.model.reporting;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "user_generated_reports_overrides")
@CompoundIndex(name = "user_symbol_reporttype", def = "{'userId': 1, 'symbol': 1, 'reportType': 1}", unique = true)
public class UserReportOverride {
    @Id
    private String id;
    private String userId;
    private String symbol;
    private ReportType reportType;
    private List<ReportItem> items;
    private boolean needsReview;
    private LocalDateTime overriddenAt;
    private LocalDateTime sharedReportGeneratedAt;
}
