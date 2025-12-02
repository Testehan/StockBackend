package com.testehan.finana.model.filing;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class QuarterlyEarningsTranscript {
    private String quarter;

    @JsonProperty("transcript")
    private List<Transcript> transcript;

    private LocalDateTime lastUpdated;
}
