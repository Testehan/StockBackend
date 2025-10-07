package com.testehan.finana.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ChecklistReport {
    private List<ReportItem> items = new ArrayList<>();
    private LocalDateTime generatedAt = LocalDateTime.now();
}
