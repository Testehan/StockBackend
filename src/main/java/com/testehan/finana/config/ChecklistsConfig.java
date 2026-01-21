package com.testehan.finana.config;

import com.testehan.finana.model.llm.responses.FerolMoatAnalysisLlmResponse;
import com.testehan.finana.model.llm.responses.FerolNegativesAnalysisLlmResponse;
import com.testehan.finana.model.llm.responses.TAMScoreExplanationResponse;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.model.reporting.ReportType;
import com.testehan.finana.service.reporting.calc.ReportItemCalculator;
import com.testehan.finana.service.reporting.calc.negatives.CurrencyRiskCalculator;
import com.testehan.finana.service.reporting.calc.negatives.DilutionRiskCalculator;
import com.testehan.finana.service.reporting.calc.negatives.HeadquarterRiskCalculator;
import com.testehan.finana.service.reporting.calc.negatives.MultipleRisksCalculator;
import com.testehan.finana.service.reporting.calc.positives.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;
import java.util.List;

@Configuration
public class ChecklistsConfig {

    @Bean
    public List<ReportItemCalculator> ferolCalculators(
            FinancialResilienceCalculator financialResilienceCalculator,
            GrossMarginCalculator grossMarginCalculator,
            RoicCalculator roicCalculator,
            FcfCalculator fcfCalculator,
            EpsCalculator epsCalculator,
            MoatCalculator moatCalculator,
            OptionalityCalculator optionalityCalculator,
            OrganicGrowthRunawayCalculator organicGrowthRunawayCalculator,
            TopDogCalculator topDogCalculator,
            OperatingLeverageCalculator operatingLeverageCalculator,
            AcquisitionsCalculator acquisitionsCalculator,
            CyclicalityCalculator cyclicalityCalculator,
            RecurringRevenueCalculator recurringRevenueCalculator,
            PricingPowerCalculator pricingPowerCalculator,
            CultureCalculator cultureCalculator,
            SoulInTheGameCalculator soulInTheGameCalculator,
            InsiderOwnershipCalculator insiderOwnershipCalculator,
            MissionStatementCalculator missionStatementCalculator,
            PerformanceVsSP500Calculator performanceVsSP500Calculator,
            ShareholderFriendlyActivityCalculator shareholderFriendlyActivityCalculator,
            BeatingEarningsExpectationsCalculator beatingEarningsExpectationsCalculator,
            MultipleRisksCalculator multipleRisksCalculator,
            DilutionRiskCalculator dilutionRiskCalculator,
            HeadquarterRiskCalculator headquarterRiskCalculator,
            CurrencyRiskCalculator currencyRiskCalculator
    ) {
        return List.of(
                (ticker, reportType, sseEmitter) -> List.of(financialResilienceCalculator.calculate(ticker, sseEmitter)),
                (ticker, reportType, sseEmitter) -> List.of(grossMarginCalculator.calculate(ticker, sseEmitter)),
                (ticker, reportType, sseEmitter) -> List.of(roicCalculator.calculate(ticker, sseEmitter)),
                (ticker, reportType, sseEmitter) -> List.of(fcfCalculator.calculate(ticker, sseEmitter)),
                (ticker, reportType, sseEmitter) -> List.of(epsCalculator.calculate(ticker, sseEmitter)),
                (ticker, reportType, sseEmitter) -> {
                    FerolMoatAnalysisLlmResponse analysis = moatCalculator.calculate(ticker, sseEmitter);
                    return List.of(
                            new ReportItem("networkEffect", analysis.getNetworkEffectScore(), analysis.getNetworkEffectExplanation()),
                            new ReportItem("switchingCosts", analysis.getSwitchingCostsScore(), analysis.getSwitchingCostsExplanation()),
                            new ReportItem("durableCostAdvantage", analysis.getDurableCostAdvantageScore(), analysis.getDurableCostAdvantageExplanation()),
                            new ReportItem("intangibles", analysis.getIntangiblesScore(), analysis.getIntangiblesExplanation()),
                            new ReportItem("counterPositioning", analysis.getCounterPositioningScore(), analysis.getCounterPositioningExplanation()),
                            new ReportItem("moatDirection", analysis.getMoatDirectionScore(), analysis.getMoatDirectionExplanation())
                    );
                },
                sequential((ticker, reportType, sseEmitter) -> List.of(optionalityCalculator.calculate(ticker, sseEmitter))),
                sequential((ticker, reportType, sseEmitter) -> List.of(organicGrowthRunawayCalculator.calculate(ticker, sseEmitter))),
                sequential((ticker, reportType, sseEmitter) -> List.of(topDogCalculator.calculate(ticker, sseEmitter))),
                sequential((ticker, reportType, sseEmitter) -> List.of(operatingLeverageCalculator.calculate(ticker, sseEmitter))),
                (ticker, reportType, sseEmitter) -> List.of(acquisitionsCalculator.calculate(ticker, sseEmitter)),
                sequential((ticker, reportType, sseEmitter) -> List.of(cyclicalityCalculator.calculate(ticker, sseEmitter))),
                sequential((ticker, reportType, sseEmitter) -> List.of(recurringRevenueCalculator.calculate(ticker, sseEmitter))),
                sequential((ticker, reportType, sseEmitter) -> List.of(pricingPowerCalculator.calculate(ticker, sseEmitter))),
                sequential((ticker, reportType, sseEmitter) -> List.of(cultureCalculator.calculate(ticker, sseEmitter))),
                sequential((ticker, reportType, sseEmitter) -> List.of(soulInTheGameCalculator.calculate(ticker, sseEmitter))),
                sequential((ticker, reportType, sseEmitter) -> List.of(insiderOwnershipCalculator.calculate(ticker, sseEmitter, reportType))),
                sequential((ticker, reportType, sseEmitter) -> List.of(missionStatementCalculator.calculate(ticker, sseEmitter))),
                (ticker, reportType, sseEmitter) -> List.of(performanceVsSP500Calculator.calculateUpsidePerformance(ticker, sseEmitter)),
                (ticker, reportType, sseEmitter) -> List.of(shareholderFriendlyActivityCalculator.calculate(ticker, sseEmitter)),
                (ticker, reportType, sseEmitter) -> List.of(beatingEarningsExpectationsCalculator.calculateUpsidePerformance(ticker, sseEmitter)),
                sequential((ticker, reportType, sseEmitter) -> {
                    FerolNegativesAnalysisLlmResponse analysis = multipleRisksCalculator.calculate(ticker, sseEmitter);
                    return List.of(
                            new ReportItem("accountingIrregularities", analysis.getAccountingIrregularitiesScore(), analysis.getAccountingIrregularitiesExplanation()),
                            new ReportItem("customerConcentration", analysis.getCustomerConcentrationScore(), analysis.getCustomerConcentrationExplanation()),
                            new ReportItem("industryDisruption", analysis.getIndustryDisruptionScore(), analysis.getIndustryDisruptionExplanation()),
                            new ReportItem("outsideForces", analysis.getOutsideForcesScore(), analysis.getOutsideForcesExplanation()),
                            new ReportItem("binaryEvent", analysis.getBinaryEventScore(), analysis.getBinaryEventExplanation()),
                            new ReportItem("growthByAcquisition", analysis.getGrowthByAcquisitionScore(), analysis.getGrowthByAcquisitionExplanation()),
                            new ReportItem("complicatedFinancials", analysis.getComplicatedFinancialsScore(), analysis.getComplicatedFinancialsExplanation()),
                            new ReportItem("antitrustConcerns", analysis.getAntitrustConcernsScore(), analysis.getAntitrustConcernsExplanation())
                    );
                }),
                (ticker, reportType, sseEmitter) -> List.of(performanceVsSP500Calculator.calculateDownsidePerformance(ticker, sseEmitter)),
                (ticker, reportType, sseEmitter) -> List.of(dilutionRiskCalculator.calculate(ticker, sseEmitter)),
                (ticker, reportType, sseEmitter) -> List.of(headquarterRiskCalculator.calculate(ticker, sseEmitter)),
                (ticker, reportType, sseEmitter) -> List.of(currencyRiskCalculator.calculate(ticker, sseEmitter))
        );
    }

    @Bean
    public List<ReportItemCalculator> oneHundredBaggerCalculators(
            ReinvestmentCalculator reinvestmentCalculator,
            ReinvestmentRunwayCalculator reinvestmentRunwayCalculator,
            InsiderOwnershipCalculator insiderOwnershipCalculator,
            CapitalAllocationCalculator capitalAllocationCalculator,
            TamCalculator tamCalculator,
            ScalabilityOfModelCalculator scalabilityOfModelCalculator,
            GrowthCurveCalculator growthCurveCalculator,
            MarketCapCalculator marketCapCalculator,
            ValuationCalculator valuationCalculator,
            MoatCalculator moatCalculator
    ) {
        return List.of(
                sequential((ticker, reportType, sseEmitter) -> List.of(reinvestmentCalculator.calculate(ticker, sseEmitter))),
                sequential((ticker, reportType, sseEmitter) -> List.of(reinvestmentCalculator.calculateSustainedReturnsOnCapital(ticker, sseEmitter))),
                sequential((ticker, reportType, sseEmitter) -> List.of(reinvestmentRunwayCalculator.calculate(ticker, sseEmitter))),
                sequential((ticker, reportType, sseEmitter) -> List.of(insiderOwnershipCalculator.calculate(ticker, sseEmitter, reportType))),
                sequential((ticker, reportType, sseEmitter) -> List.of(capitalAllocationCalculator.calculate(ticker, sseEmitter))),
                sequential((ticker, reportType, sseEmitter) -> {
                    TAMScoreExplanationResponse analysis = tamCalculator.calculate(ticker, sseEmitter);
                    return List.of(
                            new ReportItem("totalAddressableMarket", analysis.getTotalAddressableMarketScore(), analysis.getTotalAddressableMarketExplanation()),
                            new ReportItem("tamPenetrationRunway", analysis.getTamPenetrationRunwayScore(), analysis.getTamPenetrationRunwayExplanation())
                    );
                }),
                sequential((ticker, reportType, sseEmitter) -> List.of(scalabilityOfModelCalculator.calculate(ticker, sseEmitter))),
                sequential((ticker, reportType, sseEmitter) -> List.of(growthCurveCalculator.calculate(ticker, sseEmitter))),
                (ticker, reportType, sseEmitter) -> List.of(marketCapCalculator.calculate(ticker)),
                sequential((ticker, reportType, sseEmitter) -> List.of(valuationCalculator.calculate(ticker, sseEmitter))),
                sequential((ticker, reportType, sseEmitter) -> List.of(moatCalculator.calculate100BaggerMoat(ticker, sseEmitter)))
        );
    }

    private static ReportItemCalculator sequential(ReportItemCalculator calc) {
        return new ReportItemCalculator() {
            @Override
            public Collection<ReportItem> calculate(String ticker, ReportType reportType, SseEmitter sseEmitter) {
                return calc.calculate(ticker, reportType, sseEmitter);
            }

            @Override
            public boolean canRunInParallel() {
                return false;
            }
        };
    }
}