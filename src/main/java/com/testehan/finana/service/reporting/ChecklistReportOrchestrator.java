package com.testehan.finana.service.reporting;

import com.testehan.finana.model.ChecklistReport;
import com.testehan.finana.model.ChecklistReportSummaryDTO;
import com.testehan.finana.model.GeneratedReport;
import com.testehan.finana.model.ReportItem;
import com.testehan.finana.model.llm.responses.FerolMoatAnalysisLlmResponse;
import com.testehan.finana.model.llm.responses.FerolNegativesAnalysisLlmResponse;
import com.testehan.finana.repository.GeneratedReportRepository;
import com.testehan.finana.service.FinancialDataOrchestrator;
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
import java.util.stream.Collectors;

@Service
public class ChecklistReportOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChecklistReportOrchestrator.class);

    private final FinancialDataOrchestrator financialDataOrchestrator;
    private final ChecklistSseService checklistSseService;
    private final ChecklistReportPersistenceService checklistReportPersistenceService;

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

    private final SoulInTheGameCalculator soulInTheGameCalculator;
    private final InsiderOwnershipCalculator insiderOwnershipCalculator;
    private final CultureCalculator cultureCalculator;
    private final MissionStatementCalculator missionStatementCalculator;

    private final PerformanceVsSP500Calculator performanceVsSP500Calculator;
    private final ShareholderFriendlyActivityCalculator shareholderFriendlyActivityCalculator;
    private final BeatingEarningsExpectationsCalculator beatingEarningsExpectationsCalculator;

    private final MultipleRisksCalculator multipleRisksCalculator;
    private final DilutionRiskCalculator dilutionRiskCalculator;
    private final HeadquarterRiskCalculator headquarterRiskCalculator;
    private final CurrencyRiskCalculator currencyRiskCalculator;

    private final GeneratedReportRepository generatedReportRepository;
    private final Executor checklistExecutor;


    public ChecklistReportOrchestrator(FinancialDataOrchestrator financialDataOrchestrator, ChecklistSseService checklistSseService, ChecklistReportPersistenceService checklistReportPersistenceService, FinancialResilienceCalculator financialResilienceCalculator, GrossMarginCalculator grossMarginCalculator, RoicCalculator roicCalculator, FcfCalculator fcfCalculator, EpsCalculator epsCalculator, MoatCalculator moatCalculator, OptionalityCalculator optionalityCalculator, OrganicGrowthRunawayCalculator organicGrowthRunawayCalculator, TopDogCalculator topDogCalculator, OperatingLeverageCalculator operatingLeverageCalculator, AcquisitionsCalculator acquisitionsCalculator, CyclicalityCalculator cyclicalityCalculator, RecurringRevenueCalculator recurringRevenueCalculator, PricingPowerCalculator pricingPowerCalculator, SoulInTheGameCalculator soulInTheGameCalculator, InsiderOwnershipCalculator insiderOwnershipCalculator, CultureCalculator cultureCalculator, MissionStatementCalculator missionStatementCalculator, PerformanceVsSP500Calculator performanceVsSP500Calculator, ShareholderFriendlyActivityCalculator shareholderFriendlyActivityCalculator, BeatingEarningsExpectationsCalculator beatingEarningsExpectationsCalculator, MultipleRisksCalculator multipleRisksCalculator, DilutionRiskCalculator dilutionRiskCalculator, HeadquarterRiskCalculator headquarterRiskCalculator, CurrencyRiskCalculator currencyRiskCalculator, GeneratedReportRepository generatedReportRepository, @Qualifier("checklistExecutor") Executor checklistExecutor) {
        this.financialDataOrchestrator = financialDataOrchestrator;
        this.checklistSseService = checklistSseService;
        this.checklistReportPersistenceService = checklistReportPersistenceService;
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
        this.soulInTheGameCalculator = soulInTheGameCalculator;
        this.insiderOwnershipCalculator = insiderOwnershipCalculator;
        this.cultureCalculator = cultureCalculator;
        this.missionStatementCalculator = missionStatementCalculator;
        this.performanceVsSP500Calculator = performanceVsSP500Calculator;
        this.shareholderFriendlyActivityCalculator = shareholderFriendlyActivityCalculator;
        this.beatingEarningsExpectationsCalculator = beatingEarningsExpectationsCalculator;
        this.multipleRisksCalculator = multipleRisksCalculator;
        this.dilutionRiskCalculator = dilutionRiskCalculator;
        this.headquarterRiskCalculator = headquarterRiskCalculator;
        this.currencyRiskCalculator = currencyRiskCalculator;
        this.generatedReportRepository = generatedReportRepository;
        this.checklistExecutor = checklistExecutor;
    }


    public SseEmitter getChecklistReport(String ticker, boolean recreateReport, String reportType) {
        SseEmitter sseEmitter = new SseEmitter(3600000L); // Timeout set to 1 hour

        if (reportType.equalsIgnoreCase("ferol")) {
            getFerolChecklistReport(ticker, recreateReport, reportType, sseEmitter);
        } else if (reportType.equalsIgnoreCase("100bagger")) {
            checklistSseService.sendSseEvent(sseEmitter, "100bagger report is not implemented yet.");
            sseEmitter.complete();
        } else {
            checklistSseService.sendSseEvent(sseEmitter, "Invalid report type.");
            sseEmitter.complete();
        }

        return sseEmitter;
    }

    private void getFerolChecklistReport(String ticker, boolean recreateReport, String reportType, SseEmitter sseEmitter) {
        checklistExecutor.execute(() -> {
            try {
                if (!recreateReport) {
                    checklistSseService.sendSseEvent(sseEmitter, "Attempting to load report from database...");
                    Optional<GeneratedReport> existingGeneratedReport = generatedReportRepository.findBySymbol(ticker);
                    if (existingGeneratedReport.isPresent()) {
                        ChecklistReport checklistReport = getReportFromGeneratedReport(existingGeneratedReport.get(), reportType);
                        if (checklistReport != null) {
                            checklistSseService.sendSseEvent(sseEmitter, "Report loaded from database.");
                            sseEmitter.send(SseEmitter.event()
                                    .name("COMPLETED")
                                    .data(checklistReport, MediaType.APPLICATION_JSON));
                            sseEmitter.complete();
                            LOGGER.info("Checklist report for {} loaded from DB and sent.", ticker);
                            return; // Exit as report is sent
                        }
                    }
                    checklistSseService.sendSseEvent(sseEmitter, "Report not found in database or incomplete, generating new report.");

                } else {
                    checklistSseService.sendSseEvent(sseEmitter, "Initiating Checklist report generation for " + ticker + "...");
                }

                checklistSseService.sendSseEvent(sseEmitter, "Ensuring financial data is present...");
                financialDataOrchestrator.ensureFinancialDataIsPresent(ticker);
                checklistSseService.sendSseEvent(sseEmitter, "Financial data check complete.");

                CompletableFuture<ReportItem> financialResilienceFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Calculating financial resilience...");
                    ReportItem item = financialResilienceCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Financial resilience calculation complete.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> grossMarginFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Calculating Gross Margin...");
                    ReportItem item = grossMarginCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Gross Margin calculation complete.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> roicFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Calculating Return on Invested Capital (ROIC)...");
                    ReportItem item = roicCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Return on Invested Capital (ROIC) calculation complete.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> fcfFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Calculating Free Cash Flow...");
                    ReportItem item = fcfCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Free Cash Flow calculation complete.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> epsFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Calculating Earnings Per Share (EPS)...");
                    ReportItem item = epsCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Earnings Per Share (EPS) calculation complete.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<FerolMoatAnalysisLlmResponse> moatsFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Thinking about moats...");
                    FerolMoatAnalysisLlmResponse item = moatCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Moats analysis is complete.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> optionalityFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Thinking about optionality...");
                    ReportItem item = optionalityCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Optionality analysis is complete.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> organicGrowthRunawayFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Thinking about organic growth runaway...");
                    ReportItem item = organicGrowthRunawayCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Organic growth runaway analysis is complete.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> topDogFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Thinking about top dog or first mover...");
                    ReportItem item = topDogCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Top dog or first mover analysis is complete.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> operatingLeverageFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Thinking about operating leverage...");
                    ReportItem item = operatingLeverageCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Operating leverage analysis is complete.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> customerAcquisitionsFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Thinking about Sales & Marketing % of gross profit...");
                    ReportItem item = acquisitionsCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Sales & Marketing % of gross profit analysis is complete.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> cyclicalityFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Thinking how cyclical is this business..");
                    ReportItem item = cyclicalityCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Business cyclicality analysis is complete.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> recurringRevenueFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Does this company have recurring revenue..?");
                    ReportItem item = recurringRevenueCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Recurring revenue analysis is complete.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> pricingPowerFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "How about their pricing power..?");
                    ReportItem item = pricingPowerCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Pricing power analysis is complete.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> cultureCalculatorFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "How is the culture..?");
                    ReportItem item = cultureCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Culture check done.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> soulInTheGameFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Soul in the game..?");
                    ReportItem item = soulInTheGameCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Soul in the game done.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> insiderOwnershipFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Skin in the game analysis...");
                    ReportItem item = insiderOwnershipCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Skin in the game done.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> missionStatementFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Mission statement..?");
                    ReportItem item = missionStatementCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Mission statement analysis is complete.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> performanceVsSP500Future = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Calculating performance vs S&P500...");
                    ReportItem item = performanceVsSP500Calculator.calculateUpsidePerformance(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Performance vs S&P500 analysis is complete.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> shareholderFriendlyActivityFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Analyzing shareholder friendly activities...");
                    ReportItem item = shareholderFriendlyActivityCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Shareholder friendly activities analysis is complete.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> beatingExpectationsFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Are they beating expectations ?...");
                    ReportItem item = beatingEarningsExpectationsCalculator.calculateUpsidePerformance(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Earnings beating analysis is complete.");
                    return item;
                }, checklistExecutor);



                // Negatives
                CompletableFuture<FerolNegativesAnalysisLlmResponse> multipleRiskFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Analysing various negatives..");
                    FerolNegativesAnalysisLlmResponse item = multipleRisksCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Various negatives analysis is done.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> performanceDownVsSP500Future = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Calculating performance vs S&P500...");
                    ReportItem item = performanceVsSP500Calculator.calculateDownsidePerformance(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Performance vs S&P500 analysis is complete.");
                    return item;
                }, checklistExecutor);


                CompletableFuture<ReportItem> dilutionRiskFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "How was the dilution in the past years..?");
                    ReportItem item = dilutionRiskCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Dilution analysis is done.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> headquartersRiskFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Any headquarters risk..?");
                    ReportItem item = headquarterRiskCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Headquarters risk analysis is done.");
                    return item;
                }, checklistExecutor);

                CompletableFuture<ReportItem> currencyRiskFuture = CompletableFuture.supplyAsync(() -> {
                    checklistSseService.sendSseEvent(sseEmitter, "Any currency risk..?");
                    ReportItem item = currencyRiskCalculator.calculate(ticker, sseEmitter);
                    checklistSseService.sendSseEvent(sseEmitter, "Currency risk analysis is done.");
                    return item;
                }, checklistExecutor);


                CompletableFuture.allOf(
                        financialResilienceFuture, grossMarginFuture, roicFuture, fcfFuture, epsFuture,
                        moatsFuture,
                        optionalityFuture, organicGrowthRunawayFuture, topDogFuture, operatingLeverageFuture,
                        customerAcquisitionsFuture, cyclicalityFuture,
                        recurringRevenueFuture, pricingPowerFuture,

                        soulInTheGameFuture,insiderOwnershipFuture,cultureCalculatorFuture,missionStatementFuture,

                        performanceVsSP500Future, shareholderFriendlyActivityFuture, beatingExpectationsFuture,

                        // negatives
                        multipleRiskFuture,
                        performanceDownVsSP500Future,
                        dilutionRiskFuture,
                        headquartersRiskFuture,
                        currencyRiskFuture)
                        .join();

                List<ReportItem> checklistReportItems = new ArrayList<>();
                checklistReportItems.add(financialResilienceFuture.get());
                checklistReportItems.add(grossMarginFuture.get());
                checklistReportItems.add(roicFuture.get());
                checklistReportItems.add(fcfFuture.get());
                checklistReportItems.add(epsFuture.get());

                FerolMoatAnalysisLlmResponse moatAnalysis = moatsFuture.get();
                checklistReportItems.add(new ReportItem("networkEffect",moatAnalysis.getNetworkEffectScore(), moatAnalysis.getNetworkEffectExplanation()));
                checklistReportItems.add(new ReportItem("switchingCosts",moatAnalysis.getSwitchingCostsScore(), moatAnalysis.getSwitchingCostsExplanation()));
                checklistReportItems.add(new ReportItem("durableCostAdvantage",moatAnalysis.getDurableCostAdvantageScore(), moatAnalysis.getDurableCostAdvantageExplanation()));
                checklistReportItems.add(new ReportItem("intangibles",moatAnalysis.getIntangiblesScore(), moatAnalysis.getIntangiblesExplanation()));
                checklistReportItems.add(new ReportItem("counterPositioning",moatAnalysis.getCounterPositioningScore(), moatAnalysis.getCounterPositioningExplanation()));
                checklistReportItems.add(new ReportItem("moatDirection",moatAnalysis.getMoatDirectionScore(), moatAnalysis.getMoatDirectionExplanation()));

                checklistReportItems.add(optionalityFuture.get());
                checklistReportItems.add(organicGrowthRunawayFuture.get());
                checklistReportItems.add(topDogFuture.get());
                checklistReportItems.add(operatingLeverageFuture.get());
                checklistReportItems.add(customerAcquisitionsFuture.get());
                checklistReportItems.add(cyclicalityFuture.get());
                checklistReportItems.add(recurringRevenueFuture.get());
                checklistReportItems.add(pricingPowerFuture.get());

                checklistReportItems.add(soulInTheGameFuture.get());
                checklistReportItems.add(insiderOwnershipFuture.get());
                checklistReportItems.add(cultureCalculatorFuture.get());
                checklistReportItems.add(missionStatementFuture.get());

                checklistReportItems.add(performanceVsSP500Future.get());
                checklistReportItems.add(shareholderFriendlyActivityFuture.get());
                checklistReportItems.add(beatingExpectationsFuture.get());

                FerolNegativesAnalysisLlmResponse negativesAnalysis = multipleRiskFuture.get();
                checklistReportItems.add(new ReportItem("accountingIrregularities",negativesAnalysis.getAccountingIrregularitiesScore(), negativesAnalysis.getAccountingIrregularitiesExplanation()));
                checklistReportItems.add(new ReportItem("customerConcentration",negativesAnalysis.getCustomerConcentrationScore(), negativesAnalysis.getCustomerConcentrationExplanation()));
                checklistReportItems.add(new ReportItem("industryDisruption",negativesAnalysis.getIndustryDisruptionScore(), negativesAnalysis.getIndustryDisruptionExplanation()));
                checklistReportItems.add(new ReportItem("outsideForces",negativesAnalysis.getOutsideForcesScore(), negativesAnalysis.getOutsideForcesExplanation()));
                checklistReportItems.add(new ReportItem("binaryEvent",negativesAnalysis.getBinaryEventScore(), negativesAnalysis.getBinaryEventExplanation()));
                checklistReportItems.add(new ReportItem("growthByAcquisition",negativesAnalysis.getGrowthByAcquisitionScore(), negativesAnalysis.getGrowthByAcquisitionExplanation()));
                checklistReportItems.add(new ReportItem("complicatedFinancials",negativesAnalysis.getComplicatedFinancialsScore(), negativesAnalysis.getComplicatedFinancialsExplanation()));
                checklistReportItems.add(new ReportItem("antitrustConcerns",negativesAnalysis.getAntitrustConcernsScore(), negativesAnalysis.getAntitrustConcernsExplanation()));
                checklistReportItems.add(performanceDownVsSP500Future.get());
                checklistReportItems.add(dilutionRiskFuture.get());
                checklistReportItems.add(headquartersRiskFuture.get());
                checklistReportItems.add(currencyRiskFuture.get());


                checklistSseService.sendSseEvent(sseEmitter, "Building and saving Checklist report...");
                ChecklistReport checklistReport = checklistReportPersistenceService.buildAndSaveReport(ticker, checklistReportItems, reportType);
                checklistSseService.sendSseEvent(sseEmitter, "Checklist report built and saved.");

                sseEmitter.send(SseEmitter.event()
                        .name("COMPLETED")
                        .data(checklistReport, MediaType.APPLICATION_JSON));

                sseEmitter.complete();
                LOGGER.info("Checklist report generation complete for {}", ticker);

            } catch (Exception e) {
                LOGGER.error("Error generating Checklist report for {}: {}", ticker, e.getMessage(), e);
                sseEmitter.completeWithError(e);
            }
        });
    }



    public ChecklistReport saveChecklistReport(String ticker, List<ReportItem> checklistReportItems, String reportType) {
        LOGGER.info("Saving Checklist report for {}", ticker);
        return checklistReportPersistenceService.buildAndSaveReport(ticker, checklistReportItems, reportType);
    }

            public List<ChecklistReportSummaryDTO> getChecklistReportsSummary(String reportType) {

                return generatedReportRepository.findAll().stream()

                        .map(generatedReport -> {
                            String ticker = generatedReport.getSymbol();
                            ChecklistReport ferolReport = generatedReport.getFerolReport();
                            Double totalScore = ferolReport.getItems().stream()
                                    .mapToDouble(item -> item.getScore() != null ? item.getScore() : 0.0)
                                    .sum();
                            return new com.testehan.finana.model.ChecklistReportSummaryDTO(ticker, totalScore, ferolReport.getGeneratedAt());
                        })
                        .collect(Collectors.toList());

            }

        

            private ChecklistReport getReportFromGeneratedReport(GeneratedReport generatedReport, String reportType) {

                if (reportType.equalsIgnoreCase("ferol")) {

                    return generatedReport.getFerolReport();

                } else if (reportType.equalsIgnoreCase("100bagger")) {

                    return generatedReport.getOneHundredBaggerReport();

                }

                return null;

            }

        }

        

    