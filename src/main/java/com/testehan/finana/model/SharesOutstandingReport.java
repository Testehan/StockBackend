package com.testehan.finana.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SharesOutstandingReport {
    @JsonProperty("date")
    private String date;
    @JsonProperty("shares_outstanding_diluted")
    private String sharesOutstandingDiluted;
    @JsonProperty("shares_outstanding_basic")
    private String sharesOutstandingBasic;
}
