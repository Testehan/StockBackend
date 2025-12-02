package com.testehan.finana.model.qa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class StockSentiment {
    private String ticker;
    private int score;
    private String label;
    private int sourcesAnalyzed;
    private String summary;
    private List<String> catalysts;
    private LocalDateTime date;

    // todo add another array sources ..which will contain the links used in the calculation of the sentiment

    public String toJson() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting StockSentiment to JSON", e);
        }
    }
}
