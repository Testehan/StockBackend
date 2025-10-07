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
}
