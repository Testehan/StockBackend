package com.testehan.finana.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FmpEarning implements Serializable {
    @JsonProperty("date")
    private String date;
    @JsonProperty("symbol")
    private String symbol;
    @JsonProperty("epsActual")
    private Double epsActual;
    @JsonProperty("epsEstimated")
    private Double epsEstimated;
    @JsonProperty("revenueActual")
    private Long revenueActual;
    @JsonProperty("revenueEstimated")
    private Long revenueEstimated;
    @JsonProperty("lastUpdated")
    private String lastUpdated;
}
