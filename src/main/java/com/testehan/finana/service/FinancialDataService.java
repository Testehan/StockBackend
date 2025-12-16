package com.testehan.finana.service;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.finstatement.*;
import com.testehan.finana.model.ratio.FinancialRatiosData;
import com.testehan.finana.model.ratio.FinancialRatiosReport;
import com.testehan.finana.model.ratio.FmpRatios;
import com.testehan.finana.model.ratio.FmpRatiosTtm;
import com.testehan.finana.repository.FinancialRatiosRepository;
import com.testehan.finana.repository.GeneratedReportRepository;
import com.testehan.finana.util.DateUtils;
import com.testehan.finana.util.FinancialRatiosCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FinancialDataService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinancialDataService.class);

    private final FMPService fmpService;

    private final CompanyDataService companyDataService;
    private final FinancialRatiosRepository financialRatiosRepository;
    private final GeneratedReportRepository generatedReportRepository;
    private final FinancialStatementService financialStatementService;

    private final FinancialRatiosCalculator financialRatiosCalculator;
    private final QuoteService quoteService;
    private final DateUtils dateUtils;

    public FinancialDataService(FMPService fmpService, CompanyDataService companyDataService, FinancialStatementService financialStatementService, FinancialRatiosRepository financialRatiosRepository, GeneratedReportRepository generatedReportRepository, FinancialRatiosCalculator financialRatiosCalculator, QuoteService quoteService, DateUtils dateUtils) {
        this.fmpService = fmpService;
        this.companyDataService = companyDataService;
        this.financialStatementService = financialStatementService;
        this.financialRatiosRepository = financialRatiosRepository;
        this.generatedReportRepository = generatedReportRepository;
        this.financialRatiosCalculator = financialRatiosCalculator;
        this.quoteService = quoteService;
        this.dateUtils = dateUtils;
    }



    public Mono<Optional<FinancialRatiosData>> getFinancialRatios(String symbol) {
            return Mono.fromCallable(() -> financialRatiosRepository.findBySymbol(symbol))
                    .flatMap(existingRatiosData -> {

                        if (existingRatiosData.isEmpty() || !dateUtils.isRecent(existingRatiosData.get().getLastUpdated(),  DateUtils.CACHE_ONE_MONTH)) {
                            return calculateAndSaveRatios(symbol)
                                    .flatMap(data -> {
                                        updateFmpData(symbol, data);
                                        return Mono.just(data);
                                    })
                                    .map(Optional::of)
                                    .defaultIfEmpty(Optional.empty());
                        }
                        // Data exists - return directly without fetching FMP
                        return Mono.just(existingRatiosData);
                    });
    }

    private void updateFmpData(String ticker, FinancialRatiosData data) {
        updateFinancialRatiosFromFmp(ticker, data);
        updateTtmFinancialRatios(ticker, data);
    }

    private Mono<FinancialRatiosData> calculateAndSaveRatios(String symbol) {
        return Mono.zip(
                companyDataService.getCompanyOverview(symbol),
                financialStatementService.getIncomeStatements(symbol),
                financialStatementService.getBalanceSheet(symbol),
                financialStatementService.getCashFlow(symbol)
        ).flatMap(tuple -> {
            List<CompanyOverview> companyOverviews = tuple.getT1();
            IncomeStatementData incomeStatementData = tuple.getT2();
            BalanceSheetData balanceSheetData = tuple.getT3();
            CashFlowData cashFlowData = tuple.getT4();

            if (companyOverviews.isEmpty() || incomeStatementData == null 
                    || balanceSheetData == null || cashFlowData == null) {
                return Mono.empty();
            }

            CompanyOverview companyOverview = companyOverviews.getFirst();

            FinancialRatiosData financialRatiosData = financialRatiosRepository.findBySymbol(symbol).orElse(new FinancialRatiosData());
            financialRatiosData.setSymbol(symbol);
            financialRatiosData.setAnnualReports(new ArrayList<>());
            financialRatiosData.setQuarterlyReports(new ArrayList<>());

            // Process Annual Reports
            processAndAddReports(symbol, companyOverview, incomeStatementData.getAnnualReports(), balanceSheetData.getAnnualReports(), cashFlowData.getAnnualReports(), financialRatiosData.getAnnualReports());
            // Process Quarterly Reports
            processAndAddReports(symbol, companyOverview, incomeStatementData.getQuarterlyReports(), balanceSheetData.getQuarterlyReports(), cashFlowData.getQuarterlyReports(), financialRatiosData.getQuarterlyReports());

            return Mono.just(financialRatiosRepository.save(financialRatiosData));
        });
    }

    private void processAndAddReports(String symbol,
                                       CompanyOverview companyOverview,
                                       List<IncomeReport> incomeReports,
                                       List<BalanceSheetReport> balanceSheetReports,
                                       List<CashFlowReport> cashFlowReports,
                                       List<FinancialRatiosReport> targetList)
    {

        Map<String, BalanceSheetReport> balanceSheetMap = balanceSheetReports.stream()
                .collect(Collectors.toMap(
                        r -> r.getFiscalYear() + "-" + r.getPeriod(),
                        Function.identity(), (a, b) -> a));

        Map<String, CashFlowReport> cashFlowMap = cashFlowReports.stream()
                .collect(Collectors.toMap(
                        r -> r.getFiscalYear() + "-" + r.getPeriod(),
                        Function.identity(), (a, b) -> a));

        for (IncomeReport incomeReport : incomeReports) {
            String key = incomeReport.getFiscalYear() + "-" + incomeReport.getPeriod();

            // Find corresponding reports
            BalanceSheetReport balanceSheet = balanceSheetMap.get(key);
            CashFlowReport cashFlow = cashFlowMap.get(key);

            if (balanceSheet == null || cashFlow == null) {
                continue; // Skip if we don't have all required reports
            }

            // Get stock price for the report date
            BigDecimal stockPrice = getStockPriceForDate(symbol, incomeReport.getDate());

            FinancialRatiosReport ratios = financialRatiosCalculator.calculateRatios(
                    companyOverview, incomeReport, balanceSheet, cashFlow, stockPrice);
            targetList.add(ratios);

        }
    }

    private BigDecimal getStockPriceForDate(String symbol, String fiscalDateEnding) {
        try {
            LocalDate date = LocalDate.parse(fiscalDateEnding, DateTimeFormatter.ISO_DATE);
            return quoteService.getStockQuoteByDate(symbol, date)
                    .map(quote -> new BigDecimal(quote.getAdjClose()))
                    .orElse(null);
        } catch (Exception e) {
            LOGGER.debug("Could not get stock price for {} on date {}: {}", symbol, fiscalDateEnding, e.getMessage());
            return null;
        }
    }

    private void updateTtmFinancialRatios(String ticker, FinancialRatiosData ratiosData) {
        final FinancialRatiosData dataToUpdate = ratiosData != null ? ratiosData : new FinancialRatiosData();
        if (dataToUpdate.getSymbol() == null) {
            dataToUpdate.setSymbol(ticker);
        }

        fmpService.getFinancialRatiosTtm(ticker)
                .filter(fmpRatios -> hasNonNullValues(fmpRatios))
                .map(fmpRatios -> {
                    FinancialRatiosReport report = new FinancialRatiosReport();
                    report.setDate(LocalDateTime.now().toLocalDate().toString());

                    if (fmpRatios.getPriceToEarningsRatioTTM() != null) {
                        report.setPeRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToEarningsRatioTTM()));
                    }
                    if (fmpRatios.getPriceToEarningsGrowthRatioTTM() != null) {
                        report.setPriceToEarningsGrowthRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToEarningsGrowthRatioTTM()));
                    }
                    if (fmpRatios.getForwardPriceToEarningsGrowthRatioTTM() != null) {
                        report.setForwardPriceToEarningsGrowthRatio(java.math.BigDecimal.valueOf(fmpRatios.getForwardPriceToEarningsGrowthRatioTTM()));
                    }
                    if (fmpRatios.getPriceToBookRatioTTM() != null) {
                        report.setPbRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToBookRatioTTM()));
                    }
                    if (fmpRatios.getPriceToSalesRatioTTM() != null) {
                        report.setPriceToSalesRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToSalesRatioTTM()));
                    }
                    if (fmpRatios.getPriceToFreeCashFlowRatioTTM() != null) {
                        report.setPfcfRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToFreeCashFlowRatioTTM()));
                    }
                    if (fmpRatios.getPriceToOperatingCashFlowRatioTTM() != null) {
                        report.setPocfratio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToOperatingCashFlowRatioTTM()));
                    }
                    if (fmpRatios.getPriceToFairValueTTM() != null) {
                        report.setPriceToFairValue(java.math.BigDecimal.valueOf(fmpRatios.getPriceToFairValueTTM()));
                    }
                    if (fmpRatios.getEnterpriseValueMultipleTTM() != null) {
                        report.setEnterpriseValueMultiple(java.math.BigDecimal.valueOf(fmpRatios.getEnterpriseValueMultipleTTM()));
                    }
                    dataToUpdate.setTtmReport(report);
                    dataToUpdate.setLastUpdated(LocalDateTime.now());
                    return dataToUpdate;
                })
                .doOnSuccess(data -> {
                    if (data != null) {
                        financialRatiosRepository.save(data);
                    }
                })
                .doOnError(e -> {
                    LOGGER.warn("API call failed for TTM financial ratios of {}. Keeping existing data.", ticker);
                    if (ratiosData != null) {
                        financialRatiosRepository.save(ratiosData);
                    }
                })
                .subscribe();
    }


    private void updateFinancialRatiosFromFmp(String ticker, FinancialRatiosData ratiosData) {
        final FinancialRatiosData dataToUpdate = ratiosData != null ? ratiosData : new FinancialRatiosData();
        if (dataToUpdate.getSymbol() == null) {
            dataToUpdate.setSymbol(ticker);
        }

        fmpService.getFinancialRatios(ticker)
                .map(reports -> { // reports is List<FmpRatios>
                    List<FinancialRatiosReport> annualReports = dataToUpdate.getAnnualReports();
                    if (annualReports == null) {
                        annualReports = new ArrayList<>();
                        dataToUpdate.setAnnualReports(annualReports);
                    }

                    Map<String, FinancialRatiosReport> reportsByYear = annualReports.stream()
                            .collect(Collectors.toMap(r -> r.getDate() != null ? r.getDate().substring(0, 4) : "", java.util.function.Function.identity(), (a, b) -> a));

                    for (FmpRatios fmpRatios : reports) {
                        String year = fmpRatios.getDate() != null ? fmpRatios.getDate().substring(0, 4) : "";
                        FinancialRatiosReport report = reportsByYear.get(year);

                        if (report == null) {
                           continue; // because right now i dont want years with only data from FMP when dealing with ratios
                        }

                        if (fmpRatios.getPriceToEarningsRatio() != null) {
                            report.setPeRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToEarningsRatio()));
                        }
                        if (fmpRatios.getPriceToEarningsGrowthRatio() != null) {
                            report.setPriceToEarningsGrowthRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToEarningsGrowthRatio()));
                        }
                        if (fmpRatios.getForwardPriceToEarningsGrowthRatio() != null) {
                            report.setForwardPriceToEarningsGrowthRatio(java.math.BigDecimal.valueOf(fmpRatios.getForwardPriceToEarningsGrowthRatio()));
                        }
                        if (fmpRatios.getPriceToBookRatio() != null) {
                            report.setPbRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToBookRatio()));
                        }
                        if (fmpRatios.getPriceToSalesRatio() != null) {
                            report.setPriceToSalesRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToSalesRatio()));
                        }
                        if (fmpRatios.getPriceToFreeCashFlowRatio() != null) {
                            report.setPfcfRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToFreeCashFlowRatio()));
                        }
                        if (fmpRatios.getPriceToOperatingCashFlowRatio() != null) {
                            report.setPocfratio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToOperatingCashFlowRatio()));
                        }
                        if (fmpRatios.getPriceToFairValue() != null) {
                            report.setPriceToFairValue(java.math.BigDecimal.valueOf(fmpRatios.getPriceToFairValue()));
                        }
                        if (fmpRatios.getEnterpriseValueMultiple() != null) {
                            report.setEnterpriseValueMultiple(java.math.BigDecimal.valueOf(fmpRatios.getEnterpriseValueMultiple()));
                        }
                    }

                    dataToUpdate.setLastUpdated(LocalDateTime.now());
                    return dataToUpdate;
                })
                .doOnSuccess(data -> {
                    if (data != null) {
                        financialRatiosRepository.save(data);
                    }
                })
                .doOnError(e -> {
                    LOGGER.warn("API call failed for financial ratios of {}. Keeping existing data.", ticker);
                })
                .subscribe();
    }

    public boolean hasFinancialRatios(String symbol) {
        return financialRatiosRepository.findBySymbol(symbol).isPresent();
    }

    public boolean hasGeneratedReport(String symbol) {
        return generatedReportRepository.findBySymbol(symbol).isPresent();
    }

    private boolean hasNonNullValues(FmpRatiosTtm fmpRatios) {
        if (fmpRatios == null) return false;
        return fmpRatios.getPriceToEarningsRatioTTM() != null
                || fmpRatios.getPriceToBookRatioTTM() != null
                || fmpRatios.getPriceToSalesRatioTTM() != null
                || fmpRatios.getPriceToFreeCashFlowRatioTTM() != null
                || fmpRatios.getEnterpriseValueMultipleTTM() != null;
    }
}