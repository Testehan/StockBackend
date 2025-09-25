
package com.testehan.finana.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GlobalQuote {
    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("date")
    private String date;

    @JsonProperty("adjOpen")
    private String adjOpen;

    @JsonProperty("adjHigh")
    private String adjHigh;

    @JsonProperty("adjLow")
    private String adjLow;

    @JsonProperty("adjClose")
    private String adjClose;

    @JsonProperty("volume")
    private String volume;

    @JsonProperty("price")
    private String price;
}
