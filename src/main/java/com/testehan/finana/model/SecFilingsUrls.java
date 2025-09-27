
package com.testehan.finana.model;

import org.springframework.data.annotation.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "sec_fillings_url")
@Data
@NoArgsConstructor
public class SecFilingsUrls {
    @Id
    private String symbol;
    private List<SecFilingUrlData> filings;
    private LocalDateTime lastUpdated;

    public SecFilingsUrls(String symbol, List<SecFilingUrlData> filings) {
        this.symbol = symbol;
        this.filings = filings;
    }
}
