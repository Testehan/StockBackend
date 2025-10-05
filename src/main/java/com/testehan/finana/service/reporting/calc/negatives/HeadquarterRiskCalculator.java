package com.testehan.finana.service.reporting.calc.negatives;

import com.testehan.finana.model.FerolReportItem;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.service.reporting.FerolSseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
public class HeadquarterRiskCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeadquarterRiskCalculator.class);

    private final CompanyOverviewRepository companyOverviewRepository;
    private final FerolSseService ferolSseService;

    public HeadquarterRiskCalculator(CompanyOverviewRepository companyOverviewRepository, FerolSseService ferolSseService) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.ferolSseService = ferolSseService;
    }

    public FerolReportItem calculate(String ticker, SseEmitter sseEmitter) {
        var companyOverviewOptional = companyOverviewRepository.findBySymbol(ticker);

        if (companyOverviewOptional.isPresent() && !Objects.isNull(companyOverviewOptional.get().getCountry()))
        {
            var companyCountry= companyOverviewOptional.get().getCountry();

            return identifyDomesticKey(companyCountry);

        } else {
            String errorMessage = "Operation 'calculateHeadquartersRisk' failed.";
            LOGGER.error(errorMessage + " No company overview data or country found for {}",ticker);
            ferolSseService.sendSseErrorEvent(sseEmitter, errorMessage);
        }

        return new FerolReportItem("headquarters", -10, "Something went wrong and score could not be calculated ");


    }

    private FerolReportItem identifyDomesticKey(String companyCountry) {
        List<String> noHeadquartersRisk = Arrays.asList("United States", "USA", "U.S.", "United States of America", "US",
                "Australia", "Canada", "Germany", "Netherlands", "Singapore", "SG", "Switzerland", "Denmark", "Norway",
                "Sweden", "Liechtenstein", "Luxembourg");

        // TODO for now, i only care mostly about US stocks..
        if (containsIgnoreCase(companyCountry, noHeadquartersRisk)) {
            return new FerolReportItem("headquarters", 0, "Headquarters are located in " + companyCountry);
        } else {
            return new FerolReportItem("headquarters", -3, "Headquarters are located in " + companyCountry);
        }
    }

    private static boolean containsIgnoreCase(String key, List<String> targets) {
        for (String target : targets) {
            // We use exact match or "Starts With" to avoid false positives
            // (e.g. avoiding "Rest of Americas" if we are looking for "Americas")
            if (key.equalsIgnoreCase(target)) return true;
            if (key.toLowerCase().startsWith(target.toLowerCase())) return true;
        }
        return false;
    }

}
