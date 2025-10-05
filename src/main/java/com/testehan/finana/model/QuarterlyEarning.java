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
public class QuarterlyEarning implements Serializable {
    @JsonProperty("fiscalDateEnding")
    private String fiscalDateEnding;

    @JsonProperty("reportedEPS")
    private String reportedEPS;

    @JsonProperty("estimatedEPS")
    private String estimatedEPS;

    @JsonProperty
    private String surprise;

    @JsonProperty
    private String surprisePercentage;
}
