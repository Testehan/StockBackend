package com.testehan.finana.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "shares_outstanding")
public class SharesOutstandingData {
    @Id
    private String id;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("status")
    private String status;

    @JsonProperty("data")
    private List<SharesOutstandingReport> data;
}
