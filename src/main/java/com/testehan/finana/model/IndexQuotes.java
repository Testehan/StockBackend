package com.testehan.finana.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Document("index_quotes")
@Getter
@Setter
@NoArgsConstructor
public class IndexQuotes implements Serializable {
    @Id
    private String symbol;
    private List<IndexData> quotes;
    private LocalDateTime lastUpdated;

    public IndexQuotes(String symbol, List<IndexData> quotes) {
        this.symbol = symbol;
        this.quotes = quotes;
    }
}
