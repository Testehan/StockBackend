package com.testehan.finana.service;

import com.testehan.finana.model.*;
import com.testehan.finana.repository.FinancialRatiosRepository;
import com.testehan.finana.repository.GeneratedReportRepository;
import com.testehan.finana.util.FinancialRatiosCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    public FinancialDataService(FMPService fmpService, CompanyDataService companyDataService, FinancialStatementService financialStatementService, FinancialRatiosRepository financialRatiosRepository, GeneratedReportRepository generatedReportRepository, FinancialRatiosCalculator financialRatiosCalculator, QuoteService quoteService) {
        this.fmpService = fmpService;
        this.companyDataService = companyDataService;
        this.financialStatementService = financialStatementService;
        this.financialRatiosRepository = financialRatiosRepository;
        this.generatedReportRepository = generatedReportRepository;
        this.financialRatiosCalculator = financialRatiosCalculator;
        this.quoteService = quoteService;
    }



    public Optional<FinancialRatiosData> getFinancialRatios(String symbol) {
        Optional<FinancialRatiosData> existingRatiosData = financialRatiosRepository.findBySymbol(symbol);

        if (existingRatiosData.isEmpty()) {
            FinancialRatiosData newRatiosData = calculateAndSaveRatios(symbol);
            return Optional.ofNullable(newRatiosData); // Return newly calculated data
        }
        return existingRatiosData;
    }

    private FinancialRatiosData calculateAndSaveRatios(String symbol) {
        CompanyOverview companyOverview = companyDataService.getCompanyOverview(symbol).block().getFirst();
        Optional<IncomeStatementData> incomeStatementDataOptional = financialStatementService.getIncomeStatements(symbol).blockOptional();
        Optional<BalanceSheetData> balanceSheetDataOptional = financialStatementService.getBalanceSheet(symbol).blockOptional();
        Optional<CashFlowData> cashFlowDataOptional = financialStatementService.getCashFlow(symbol).blockOptional();

        if (incomeStatementDataOptional.isPresent() && balanceSheetDataOptional.isPresent()
                && cashFlowDataOptional.isPresent()) {
            IncomeStatementData incomeStatementData = incomeStatementDataOptional.get();
            BalanceSheetData balanceSheetData = balanceSheetDataOptional.get();
            CashFlowData cashFlowData = cashFlowDataOptional.get();

            FinancialRatiosData financialRatiosData = financialRatiosRepository.findBySymbol(symbol)
                    .orElse(new FinancialRatiosData());
            financialRatiosData.setSymbol(symbol);
            financialRatiosData.setAnnualReports(new ArrayList<>());
            financialRatiosData.setQuarterlyReports(new ArrayList<>());

            // Process Annual Reports
            processAndAddReports(symbol, companyOverview, incomeStatementData.getAnnualReports(), balanceSheetData.getAnnualReports(), cashFlowData.getAnnualReports(), financialRatiosData.getAnnualReports()); // Update this
            // Process Quarterly Reports
            processAndAddReports(symbol, companyOverview, incomeStatementData.getQuarterlyReports(), balanceSheetData.getQuarterlyReports(), cashFlowData.getQuarterlyReports(), financialRatiosData.getQuarterlyReports()); // Update this

            return financialRatiosRepository.save(financialRatiosData);
        }
        return null; // Or throw an exception if data is not found
    }

    private void processAndAddReports(String symbol,
                                       CompanyOverview companyOverview,
                                       List<IncomeReport> incomeReports,
                                       List<BalanceSheetReport> balanceSheetReports,
                                       List<CashFlowReport> cashFlowReports,
                                       List<FinancialRatiosReport> targetList)
    {

        Map<String, BalanceSheetReport> balanceSheetMap = balanceSheetReports.stream()
                .collect(Collectors.toMap(BalanceSheetReport::getDate, Function.identity(), (a, b) -> a));

        Map<String, CashFlowReport> cashFlowMap = cashFlowReports.stream()
                .collect(Collectors.toMap(CashFlowReport::getDate, Function.identity(), (a, b) -> a));

        for (IncomeReport incomeReport : incomeReports) {
            String fiscalDateEnding = incomeReport.getDate();

            // Find corresponding reports
            BalanceSheetReport balanceSheet = balanceSheetMap.get(fiscalDateEnding);
            CashFlowReport cashFlow = cashFlowMap.get(fiscalDateEnding);

            if (balanceSheet == null || cashFlow == null) {
                continue; // Skip if we don't have all required reports
            }

            // Get stock price for the report date
            BigDecimal stockPrice = getStockPriceForDate(symbol, fiscalDateEnding);

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

    public void updateTtmFinancialRatios(String ticker) {
        fmpService.getFinancialRatiosTtm(ticker)
                .map(fmpRatios -> {
                    FinancialRatiosData data = financialRatiosRepository.findBySymbol(ticker).orElse(new FinancialRatiosData());
                    data.setSymbol(ticker);

                    FinancialRatiosReport report = new FinancialRatiosReport();
                    report.setDate(fmpRatios.getDate());

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
                    data.setTtmReport(report);
                    return data;
                })
                .doOnSuccess(financialRatiosRepository::save)
                .doOnError(e -> LOGGER.error("Error with TTM financial ratios for " + ticker, e)).block();
    }


    public void updateFinancialRatiosFromFmp(String ticker) {
        fmpService.getFinancialRatios(ticker)
                .map(reports -> { // reports is List<FmpRatios>
                    FinancialRatiosData data = financialRatiosRepository.findBySymbol(ticker).orElse(new FinancialRatiosData());

                    data.setSymbol(ticker);
                    List<FinancialRatiosReport> annualReports = data.getAnnualReports();
                    if (annualReports == null) {
                        annualReports = new ArrayList<>();
                        data.setAnnualReports(annualReports);
                    }

                    Map<String, FinancialRatiosReport> reportsByDate = annualReports.stream()
                            .collect(Collectors.toMap(FinancialRatiosReport::getDate, java.util.function.Function.identity()));

                    for (FmpRatios fmpRatios : reports) {
                        FinancialRatiosReport report = reportsByDate.get(fmpRatios.getDate());

                        if (report == null) {
                            report = new FinancialRatiosReport();
                            report.setDate(fmpRatios.getDate());
                            annualReports.add(report);
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

                    return data;
                })
                .doOnSuccess(financialRatiosRepository::save)
                .doOnError(e -> LOGGER.error("Error with financial ratios for " + ticker, e)).block();
    }

    public boolean hasFinancialRatios(String symbol) {
        return financialRatiosRepository.findBySymbol(symbol).isPresent();
    }

    public boolean hasGeneratedReport(String symbol) {
        return generatedReportRepository.findBySymbol(symbol).isPresent();
    }
}
