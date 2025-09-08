package com.testehan.finana.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Estimate {
    private String date;
    private String horizon;
    @JsonProperty("eps_estimate_average")
    private String epsEstimateAverage;
    @JsonProperty("eps_estimate_high")
    private String epsEstimateHigh;
    @JsonProperty("eps_estimate_low")
    private String epsEstimateLow;
    @JsonProperty("eps_estimate_analyst_count")
    private String epsEstimateAnalystCount;
    @JsonProperty("eps_estimate_average_7_days_ago")
    private String epsEstimateAverage7DaysAgo;
    @JsonProperty("eps_estimate_average_30_days_ago")
    private String epsEstimateAverage30DaysAgo;
    @JsonProperty("eps_estimate_average_60_days_ago")
    private String epsEstimateAverage60DaysAgo;
    @JsonProperty("eps_estimate_average_90_days_ago")
    private String epsEstimateAverage90DaysAgo;
    @JsonProperty("eps_estimate_revision_up_trailing_7_days")
    private String epsEstimateRevisionUpTrailing7Days;
    @JsonProperty("eps_estimate_revision_down_trailing_7_days")
    private String epsEstimateRevisionDownTrailing7Days;
    @JsonProperty("eps_estimate_revision_up_trailing_30_days")
    private String epsEstimateRevisionUpTrailing30Days;
    @JsonProperty("eps_estimate_revision_down_trailing_30_days")
    private String epsEstimateRevisionDownTrailing30Days;
    @JsonProperty("revenue_estimate_average")
    private String revenueEstimateAverage;
    @JsonProperty("revenue_estimate_high")
    private String revenueEstimateHigh;
    @JsonProperty("revenue_estimate_low")
    private String revenueEstimateLow;
    @JsonProperty("revenue_estimate_analyst_count")
    private String revenueEstimateAnalystCount;
}
