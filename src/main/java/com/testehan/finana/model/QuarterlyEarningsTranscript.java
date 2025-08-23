package com.testehan.finana.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class QuarterlyEarningsTranscript {
    private String quarter;

    @JsonProperty("transcript")
    private List<Transcript> transcript;
}
