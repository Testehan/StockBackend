package com.testehan.finana.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "revenue_segmentation_data")
public class RevenueSegmentationData {
    @Id
    private String id;

    @Indexed(unique = true)
    private String symbol;

    private List<RevenueSegmentationReport> annualReports;
    private List<RevenueSegmentationReport> quarterlyReports;
}
