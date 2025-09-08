package com.testehan.finana.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Document("earnings_estimates")
public class EarningsEstimate {
    @Id
    private String symbol;
    private List<Estimate> estimates;
    private LocalDateTime lastUpdated;
}
