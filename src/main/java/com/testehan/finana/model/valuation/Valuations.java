package com.testehan.finana.model.valuation;

import com.testehan.finana.model.valuation.dcf.DcfValuation;
import com.testehan.finana.model.valuation.dcf.ReverseDcfValuation;
import com.testehan.finana.model.valuation.growth.GrowthValuation;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document("valuations")
@Data
public class Valuations {
    @Id
    private String id;
    private String ticker;
    private String userEmail;
    private List<DcfValuation> dcfValuations = new ArrayList<>();
    private List<ReverseDcfValuation> reverseDcfValuations = new ArrayList<>();
    private List<GrowthValuation> growthValuations = new ArrayList<>();
}
