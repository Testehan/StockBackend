package com.testehan.finana.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportItem {
    private String name;
    private Integer score;
    private String explanation;
    private String personalNotes;

    public ReportItem(String name, Integer score, String explanation) {
        this.name = name;
        this.score = score;
        this.explanation = explanation;
    }
}
