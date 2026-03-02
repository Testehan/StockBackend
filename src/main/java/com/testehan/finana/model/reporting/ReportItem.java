package com.testehan.finana.model.reporting;

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
    private String type;

    public ReportItem(String name, Integer score, String explanation) {
        this.name = name;
        this.score = score;
        this.explanation = explanation;
        this.type = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
