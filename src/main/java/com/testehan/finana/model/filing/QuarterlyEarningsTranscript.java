package com.testehan.finana.model.filing;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class QuarterlyEarningsTranscript {
    private String quarter;

    @JsonProperty("transcript")
    private List<Transcript> transcript;

    private LocalDateTime lastUpdated;

    private Map<String, String> transcriptAnalysisAnswers = new HashMap<>();
}
