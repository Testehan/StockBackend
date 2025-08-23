package com.testehan.finana.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "generated_reports")
public class GeneratedReport {
    @Id
    private String symbol;
    private FerolReport ferolReport;
}
