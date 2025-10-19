package com.testehan.finana.model.valuation;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document("valuations")
@Data
public class Valuations {
    @Id
    private String ticker;
    private List<DcfValuation> dcfValuations = new ArrayList<>();
}
