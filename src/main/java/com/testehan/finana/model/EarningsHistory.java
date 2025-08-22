package com.testehan.finana.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "earnings_histories")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EarningsHistory implements Serializable {

    @Id
    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("annualEarnings")
    private List<AnnualEarning> annualEarnings;

    @JsonProperty("quarterlyEarnings")
    private List<QuarterlyEarning> quarterlyEarnings;
}
