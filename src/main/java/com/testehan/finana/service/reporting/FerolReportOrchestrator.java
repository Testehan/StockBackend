package com.testehan.finana.service.reporting;

import com.testehan.finana.model.FerolReport;
import com.testehan.finana.model.FerolReportItem;
import com.testehan.finana.model.GeneratedReport;
import com.testehan.finana.model.llm.responses.FerolMoatAnalysisLlmResponse;
import com.testehan.finana.model.llm.responses.FerolNegativesAnalysisLlmResponse;
import com.testehan.finana.repository.GeneratedReportRepository;
import com.testehan.finana.service.FinancialDataService;
import com.testehan.finana.service.reporting.calc.negatives.CurrencyRiskCalculator;
import com.testehan.finana.service.reporting.calc.negatives.DilutionRiskCalculator;
import com.testehan.finana.service.reporting.calc.negatives.HeadquarterRiskCalculator;
import com.testehan.finana.service.reporting.calc.negatives.MultipleRisksCalculator;
import com.testehan.finana.service.reporting.calc.positives.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class FerolReportOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(FerolReportOrchestrator.class);

    private final FinancialDataService financialDataService;
    private final FerolSseService ferolSseService;
    private final FerolReportPersistenceService ferolReportPersistenceService;

    private final FinancialResilienceCalculator financialResilienceCalculator;
    private final GrossMarginCalculator grossMarginCalculator;
    private final RoicCalculator roicCalculator;
    private final FcfCalculator fcfCalculator;
    private final EpsCalculator epsCalculator;
    private final MoatCalculator moatCalculator;
    private final OptionalityCalculator optionalityCalculator;
    private final OrganicGrowthRunawayCalculator organicGrowthRunawayCalculator;
    private final TopDogCalculator topDogCalculator;
    private final OperatingLeverageCalculator operatingLeverageCalculator;
    private final AcquisitionsCalculator acquisitionsCalculator;
    private final CyclicalityCalculator cyclicalityCalculator;
    private final RecurringRevenueCalculator recurringRevenueCalculator;
    private final PricingPowerCalculator pricingPowerCalculator;

    private final MultipleRisksCalculator multipleRisksCalculator;
    private final DilutionRiskCalculator dilutionRiskCalculator;
    private final HeadquarterRiskCalculator headquarterRiskCalculator;
    private final CurrencyRiskCalculator currencyRiskCalculator;

    private final GeneratedReportRepository generatedReportRepository;
    private final Executor ferolExecutor;


    public FerolReportOrchestrator(FinancialDataService financialDataService, FerolSseService ferolSseService, FerolReportPersistenceService ferolReportPersistenceService, FinancialResilienceCalculator financialResilienceCalculator, GrossMarginCalculator grossMarginCalculator, RoicCalculator roicCalculator, FcfCalculator fcfCalculator, EpsCalculator epsCalculator, MoatCalculator moatCalculator, OptionalityCalculator optionalityCalculator, OrganicGrowthRunawayCalculator organicGrowthRunawayCalculator, TopDogCalculator topDogCalculator, OperatingLeverageCalculator operatingLeverageCalculator, AcquisitionsCalculator acquisitionsCalculator, CyclicalityCalculator cyclicalityCalculator, RecurringRevenueCalculator recurringRevenueCalculator, PricingPowerCalculator pricingPowerCalculator, MultipleRisksCalculator multipleRisksCalculator, DilutionRiskCalculator dilutionRiskCalculator, HeadquarterRiskCalculator headquarterRiskCalculator, CurrencyRiskCalculator currencyRiskCalculator, GeneratedReportRepository generatedReportRepository, @Qualifier("ferolExecutor") Executor ferolExecutor) {
        this.financialDataService = financialDataService;
        this.ferolSseService = ferolSseService;
        this.ferolReportPersistenceService = ferolReportPersistenceService;
        this.financialResilienceCalculator = financialResilienceCalculator;
        this.grossMarginCalculator = grossMarginCalculator;
        this.roicCalculator = roicCalculator;
        this.fcfCalculator = fcfCalculator;
        this.epsCalculator = epsCalculator;
        this.moatCalculator = moatCalculator;
        this.optionalityCalculator = optionalityCalculator;
        this.organicGrowthRunawayCalculator = organicGrowthRunawayCalculator;
        this.topDogCalculator = topDogCalculator;
        this.operatingLeverageCalculator = operatingLeverageCalculator;
        this.acquisitionsCalculator = acquisitionsCalculator;
        this.cyclicalityCalculator = cyclicalityCalculator;
        this.recurringRevenueCalculator = recurringRevenueCalculator;
        this.pricingPowerCalculator = pricingPowerCalculator;
        this.multipleRisksCalculator = multipleRisksCalculator;
        this.dilutionRiskCalculator = dilutionRiskCalculator;
        this.headquarterRiskCalculator = headquarterRiskCalculator;
        this.currencyRiskCalculator = currencyRiskCalculator;
        this.generatedReportRepository = generatedReportRepository;
        this.ferolExecutor = ferolExecutor;
    }


    public SseEmitter getFerolReport(String ticker, boolean recreateReport) {
        SseEmitter sseEmitter = new SseEmitter(3600000L); // Timeout set to 1 hour

        ferolExecutor.execute(() -> {
            try {
                if (!recreateReport) {
                    ferolSseService.sendSseEvent(sseEmitter, "Attempting to load report from database...");
                    Optional<GeneratedReport> existingGeneratedReport = generatedReportRepository.findBySymbol(ticker);
                    if (existingGeneratedReport.isPresent() && existingGeneratedReport.get().getFerolReport() != null) {
                        FerolReport ferolReport = existingGeneratedReport.get().getFerolReport();
                        ferolSseService.sendSseEvent(sseEmitter, "Report loaded from database.");
                        sseEmitter.send(SseEmitter.event()
                                .name("COMPLETED")
                                .data(ferolReport, MediaType.APPLICATION_JSON));
                        sseEmitter.complete();
                        LOGGER.info("FEROL report for {} loaded from DB and sent.", ticker);
                        return; // Exit as report is sent
                    } else {
                        ferolSseService.sendSseEvent(sseEmitter, "Report not found in database or incomplete, generating new report.");
                    }
                } else {
                    ferolSseService.sendSseEvent(sseEmitter, "Initiating FEROL report generation for " + ticker + "...");
                }

                ferolSseService.sendSseEvent(sseEmitter, "Ensuring financial data is present...");
                financialDataService.ensureFinancialDataIsPresent(ticker);
                ferolSseService.sendSseEvent(sseEmitter, "Financial data check complete.");

                CompletableFuture<FerolReportItem> financialResilienceFuture = CompletableFuture.supplyAsync(() -> {
                    ferolSseService.sendSseEvent(sseEmitter, "Calculating financial resilience...");
                    FerolReportItem item = financialResilienceCalculator.calculate(ticker, sseEmitter);
                    ferolSseService.sendSseEvent(sseEmitter, "Financial resilience calculation complete.");
                    return item;
                }, ferolExecutor);

                CompletableFuture<FerolReportItem> grossMarginFuture = CompletableFuture.supplyAsync(() -> {
                    ferolSseService.sendSseEvent(sseEmitter, "Calculating Gross Margin...");
                    FerolReportItem item = grossMarginCalculator.calculate(ticker, sseEmitter);
                    ferolSseService.sendSseEvent(sseEmitter, "Gross Margin calculation complete.");
                    return item;
                }, ferolExecutor);

                CompletableFuture<FerolReportItem> roicFuture = CompletableFuture.supplyAsync(() -> {
                    ferolSseService.sendSseEvent(sseEmitter, "Calculating Return on Invested Capital (ROIC)...");
                    FerolReportItem item = roicCalculator.calculate(ticker, sseEmitter);
                    ferolSseService.sendSseEvent(sseEmitter, "Return on Invested Capital (ROIC) calculation complete.");
                    return item;
                }, ferolExecutor);

                CompletableFuture<FerolReportItem> fcfFuture = CompletableFuture.supplyAsync(() -> {
                    ferolSseService.sendSseEvent(sseEmitter, "Calculating Free Cash Flow...");
                    FerolReportItem item = fcfCalculator.calculate(ticker, sseEmitter);
                    ferolSseService.sendSseEvent(sseEmitter, "Free Cash Flow calculation complete.");
                    return item;
                }, ferolExecutor);

                CompletableFuture<FerolReportItem> epsFuture = CompletableFuture.supplyAsync(() -> {
                    ferolSseService.sendSseEvent(sseEmitter, "Calculating Earnings Per Share (EPS)...");
                    FerolReportItem item = epsCalculator.calculate(ticker, sseEmitter);
                    ferolSseService.sendSseEvent(sseEmitter, "Earnings Per Share (EPS) calculation complete.");
                    return item;
                }, ferolExecutor);

                CompletableFuture<FerolMoatAnalysisLlmResponse> moatsFuture = CompletableFuture.supplyAsync(() -> {
                    ferolSseService.sendSseEvent(sseEmitter, "Thinking about moats...");
                    FerolMoatAnalysisLlmResponse item = moatCalculator.calculate(ticker, sseEmitter);
                    ferolSseService.sendSseEvent(sseEmitter, "Moats analysis is complete.");
                    return item;
                }, ferolExecutor);

                CompletableFuture<FerolReportItem> optionalityFuture = CompletableFuture.supplyAsync(() -> {
                    ferolSseService.sendSseEvent(sseEmitter, "Thinking about optionality...");
                    FerolReportItem item = optionalityCalculator.calculate(ticker, sseEmitter);
                    ferolSseService.sendSseEvent(sseEmitter, "Optionality analysis is complete.");
                    return item;
                }, ferolExecutor);

                CompletableFuture<FerolReportItem> organicGrowthRunawayFuture = CompletableFuture.supplyAsync(() -> {
                    ferolSseService.sendSseEvent(sseEmitter, "Thinking about organic growth runaway...");
                    FerolReportItem item = organicGrowthRunawayCalculator.calculate(ticker, sseEmitter);
                    ferolSseService.sendSseEvent(sseEmitter, "Organic growth runaway analysis is complete.");
                    return item;
                }, ferolExecutor);

                CompletableFuture<FerolReportItem> topDogFuture = CompletableFuture.supplyAsync(() -> {
                    ferolSseService.sendSseEvent(sseEmitter, "Thinking about top dog or first mover...");
                    FerolReportItem item = topDogCalculator.calculate(ticker, sseEmitter);
                    ferolSseService.sendSseEvent(sseEmitter, "Top dog or first mover analysis is complete.");
                    return item;
                }, ferolExecutor);

                CompletableFuture<FerolReportItem> operatingLeverageFuture = CompletableFuture.supplyAsync(() -> {
                    ferolSseService.sendSseEvent(sseEmitter, "Thinking about operating leverage...");
                    FerolReportItem item = operatingLeverageCalculator.calculate(ticker, sseEmitter);
                    ferolSseService.sendSseEvent(sseEmitter, "Operating leverage analysis is complete.");
                    return item;
                }, ferolExecutor);

                CompletableFuture<FerolReportItem> customerAcquisitionsFuture = CompletableFuture.supplyAsync(() -> {
                    ferolSseService.sendSseEvent(sseEmitter, "Thinking about Sales & Marketing % of gross profit...");
                    FerolReportItem item = acquisitionsCalculator.calculate(ticker, sseEmitter);
                    ferolSseService.sendSseEvent(sseEmitter, "Sales & Marketing % of gross profit analysis is complete.");
                    return item;
                }, ferolExecutor);

                CompletableFuture<FerolReportItem> cyclicalityFuture = CompletableFuture.supplyAsync(() -> {
                    ferolSseService.sendSseEvent(sseEmitter, "Thinking how cyclical is this business..");
                    FerolReportItem item = cyclicalityCalculator.calculate(ticker, sseEmitter);
                    ferolSseService.sendSseEvent(sseEmitter, "Business cyclicality analysis is complete.");
                    return item;
                }, ferolExecutor);

                CompletableFuture<FerolReportItem> recurringRevenueFuture = CompletableFuture.supplyAsync(() -> {
                    ferolSseService.sendSseEvent(sseEmitter, "Does this company have recurring revenue..?");
                    FerolReportItem item = recurringRevenueCalculator.calculate(ticker, sseEmitter);
                    ferolSseService.sendSseEvent(sseEmitter, "Recurring revenue analysis is complete.");
                    return item;
                }, ferolExecutor);

                CompletableFuture<FerolReportItem> pricingPowerFuture = CompletableFuture.supplyAsync(() -> {
                    ferolSseService.sendSseEvent(sseEmitter, "How about their pricing power..?");
                    FerolReportItem item = pricingPowerCalculator.calculate(ticker, sseEmitter);
                    ferolSseService.sendSseEvent(sseEmitter, "Pricing power analysis is complete.");
                    return item;
                }, ferolExecutor);

                // Negatives
                CompletableFuture<FerolNegativesAnalysisLlmResponse> multipleRiskFuture = CompletableFuture.supplyAsync(() -> {
                    ferolSseService.sendSseEvent(sseEmitter, "Analysing various negatives..");
                    FerolNegativesAnalysisLlmResponse item = multipleRisksCalculator.calculate(ticker, sseEmitter);
                    ferolSseService.sendSseEvent(sseEmitter, "Various negatives analysis is done.");
                    return item;
                }, ferolExecutor);


                CompletableFuture<FerolReportItem> dilutionRiskFuture = CompletableFuture.supplyAsync(() -> {
                    ferolSseService.sendSseEvent(sseEmitter, "How was the dilution in the past years..?");
                    FerolReportItem item = dilutionRiskCalculator.calculate(ticker, sseEmitter);
                    ferolSseService.sendSseEvent(sseEmitter, "Dilution analysis is done.");
                    return item;
                }, ferolExecutor);

                CompletableFuture<FerolReportItem> headquartersRiskFuture = CompletableFuture.supplyAsync(() -> {
                    ferolSseService.sendSseEvent(sseEmitter, "Any headquarters risk..?");
                    FerolReportItem item = headquarterRiskCalculator.calculate(ticker, sseEmitter);
                    ferolSseService.sendSseEvent(sseEmitter, "Headquarters risk analysis is done.");
                    return item;
                }, ferolExecutor);

                CompletableFuture<FerolReportItem> currencyRiskFuture = CompletableFuture.supplyAsync(() -> {
                    ferolSseService.sendSseEvent(sseEmitter, "Any currency risk..?");
                    FerolReportItem item = currencyRiskCalculator.calculate(ticker, sseEmitter);
                    ferolSseService.sendSseEvent(sseEmitter, "Currency risk analysis is done.");
                    return item;
                }, ferolExecutor);


                CompletableFuture.allOf(
                        financialResilienceFuture, grossMarginFuture, roicFuture, fcfFuture, epsFuture,
                        moatsFuture,
                        optionalityFuture, organicGrowthRunawayFuture, topDogFuture, operatingLeverageFuture,
                        customerAcquisitionsFuture, cyclicalityFuture,
                        recurringRevenueFuture, pricingPowerFuture,

                        // negatives
                        multipleRiskFuture,
                        dilutionRiskFuture,
                        headquartersRiskFuture,
                        currencyRiskFuture)
                        .join();

                List<FerolReportItem> ferolReportItems = new ArrayList<>();
                ferolReportItems.add(financialResilienceFuture.get());
                ferolReportItems.add(grossMarginFuture.get());
                ferolReportItems.add(roicFuture.get());
                ferolReportItems.add(fcfFuture.get());
                ferolReportItems.add(epsFuture.get());

                FerolMoatAnalysisLlmResponse moatAnalysis = moatsFuture.get();
                ferolReportItems.add(new FerolReportItem("networkEffect",moatAnalysis.getNetworkEffectScore(), moatAnalysis.getNetworkEffectExplanation()));
                ferolReportItems.add(new FerolReportItem("switchingCosts",moatAnalysis.getSwitchingCostsScore(), moatAnalysis.getSwitchingCostsExplanation()));
                ferolReportItems.add(new FerolReportItem("durableCostAdvantage",moatAnalysis.getDurableCostAdvantageScore(), moatAnalysis.getDurableCostAdvantageExplanation()));
                ferolReportItems.add(new FerolReportItem("intangibles",moatAnalysis.getIntangiblesScore(), moatAnalysis.getIntangiblesExplanation()));
                ferolReportItems.add(new FerolReportItem("counterPositioning",moatAnalysis.getCounterPositioningScore(), moatAnalysis.getCounterPositioningExplanation()));
                ferolReportItems.add(new FerolReportItem("moatDirection",moatAnalysis.getMoatDirectionScore(), moatAnalysis.getMoatDirectionExplanation()));

                ferolReportItems.add(optionalityFuture.get());
                ferolReportItems.add(organicGrowthRunawayFuture.get());
                ferolReportItems.add(topDogFuture.get());
                ferolReportItems.add(operatingLeverageFuture.get());
                ferolReportItems.add(customerAcquisitionsFuture.get());
                ferolReportItems.add(cyclicalityFuture.get());
                ferolReportItems.add(recurringRevenueFuture.get());
                ferolReportItems.add(pricingPowerFuture.get());

                FerolNegativesAnalysisLlmResponse negativesAnalysis = multipleRiskFuture.get();
                ferolReportItems.add(new FerolReportItem("accountingIrregularities",negativesAnalysis.getAccountingIrregularitiesScore(), negativesAnalysis.getAccountingIrregularitiesExplanation()));
                ferolReportItems.add(new FerolReportItem("customerConcentration",negativesAnalysis.getCustomerConcentrationScore(), negativesAnalysis.getCustomerConcentrationExplanation()));
                ferolReportItems.add(new FerolReportItem("industryDisruption",negativesAnalysis.getIndustryDisruptionScore(), negativesAnalysis.getIndustryDisruptionExplanation()));
                ferolReportItems.add(new FerolReportItem("outsideForces",negativesAnalysis.getOutsideForcesScore(), negativesAnalysis.getOutsideForcesExplanation()));
                ferolReportItems.add(new FerolReportItem("binaryEvent",negativesAnalysis.getBinaryEventScore(), negativesAnalysis.getBinaryEventExplanation()));
                ferolReportItems.add(new FerolReportItem("growthByAcquisition",negativesAnalysis.getGrowthByAcquisitionScore(), negativesAnalysis.getGrowthByAcquisitionExplanation()));
                ferolReportItems.add(new FerolReportItem("complicatedFinancials",negativesAnalysis.getComplicatedFinancialsScore(), negativesAnalysis.getComplicatedFinancialsExplanation()));
                ferolReportItems.add(new FerolReportItem("antitrustConcerns",negativesAnalysis.getAntitrustConcernsScore(), negativesAnalysis.getAntitrustConcernsExplanation()));
                ferolReportItems.add(dilutionRiskFuture.get());
                ferolReportItems.add(headquartersRiskFuture.get());
                ferolReportItems.add(currencyRiskFuture.get());


                ferolSseService.sendSseEvent(sseEmitter, "Building and saving FEROL report...");
                FerolReport ferolReport = ferolReportPersistenceService.buildAndSaveReport(ticker, ferolReportItems);
                ferolSseService.sendSseEvent(sseEmitter, "FEROL report built and saved.");

                sseEmitter.send(SseEmitter.event()
                        .name("COMPLETED")
                        .data(ferolReport, MediaType.APPLICATION_JSON));

                sseEmitter.complete();
                LOGGER.info("FEROL report generation complete for {}", ticker);

            } catch (Exception e) {
                LOGGER.error("Error generating FEROL report for {}: {}", ticker, e.getMessage(), e);
                sseEmitter.completeWithError(e);
            }
        });

        return sseEmitter;
    }
}