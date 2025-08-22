package com.testehan.finana.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnnualEarning implements Serializable {
    @JsonProperty("fiscalDateEnding")
    private String fiscalDateEnding;

    @JsonProperty("reportedEPS")
    private String reportedEPS;
}
