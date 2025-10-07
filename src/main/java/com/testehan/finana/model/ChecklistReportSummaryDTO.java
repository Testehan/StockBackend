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
public class ChecklistReportSummaryDTO {
    private String ticker;
    private Integer totalFerolScore;
    private Integer total100BaggerScore;
    private LocalDateTime generationFerolDate;
    private LocalDateTime generation100BaggerDate;
}
