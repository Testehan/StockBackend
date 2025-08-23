
package com.testehan.finana.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "company_earnings_transcripts")
public class CompanyEarningsTranscripts {
    @Id
    private String symbol;
    private List<QuarterlyEarningsTranscript> transcripts;
}
