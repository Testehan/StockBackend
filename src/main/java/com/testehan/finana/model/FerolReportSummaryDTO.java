package com.testehan.finana.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class FerolReportSummaryDTO {
    private String ticker;
    private Double totalScore;
    private LocalDateTime generationDate;
}
