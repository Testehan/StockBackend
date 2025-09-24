package com.testehan.finana.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RevenueGeographicSegmentationReport {
    private String symbol;
    private int fiscalYear;
    private String period;
    private String reportedCurrency;
    private String date;
    private Map<String, String> data;
}
