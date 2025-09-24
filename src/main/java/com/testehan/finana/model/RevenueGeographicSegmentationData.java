package com.testehan.finana.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "revenue_geographic_segmentation_data")
public class RevenueGeographicSegmentationData {
    @Id
    private String id;

    @Indexed(unique = true)
    private String symbol;

    private List<RevenueGeographicSegmentationReport> reports;
}
