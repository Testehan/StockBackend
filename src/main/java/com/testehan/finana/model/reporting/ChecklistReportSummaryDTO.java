package com.testehan.finana.model.reporting;

import com.testehan.finana.model.user.UserStockStatus;
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
    private UserStockStatus status;

    public ChecklistReportSummaryDTO(String ticker, Integer totalFerolScore, Integer total100BaggerScore, LocalDateTime generationFerolDate, LocalDateTime generation100BaggerDate) {
        this.ticker = ticker;
        this.totalFerolScore = totalFerolScore;
        this.total100BaggerScore = total100BaggerScore;
        this.generationFerolDate = generationFerolDate;
        this.generation100BaggerDate = generation100BaggerDate;
    }
}
