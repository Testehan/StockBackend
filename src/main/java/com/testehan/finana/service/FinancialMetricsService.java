package com.testehan.finana.service;

import com.testehan.finana.model.*;
import com.testehan.finana.repository.*;
import com.testehan.finana.util.FinancialRatiosCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FinancialMetricsService {

    private final IncomeStatementRepository incomeStatementRepository;
    private final BalanceSheetRepository balanceSheetRepository;
    private final CashFlowRepository cashFlowRepository;
    private final SharesOutstandingRepository sharesOutstandingRepository;
    private final FinancialRatiosRepository financialRatiosRepository;

    private final FinancialRatiosCalculator financialRatiosCalculator;

    @Autowired
    public FinancialMetricsService(IncomeStatementRepository incomeStatementRepository,
                                   BalanceSheetRepository balanceSheetRepository,
                                   CashFlowRepository cashFlowRepository,
                                   SharesOutstandingRepository sharesOutstandingRepository,
                                   FinancialRatiosRepository financialRatiosRepository, FinancialRatiosCalculator financialRatiosCalculator) {
        this.incomeStatementRepository = incomeStatementRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.cashFlowRepository = cashFlowRepository;
        this.sharesOutstandingRepository = sharesOutstandingRepository;
        this.financialRatiosRepository = financialRatiosRepository;
        this.financialRatiosCalculator = financialRatiosCalculator;
    }

    public Optional<FinancialRatiosData> getFinancialRatios(String symbol) {
        Optional<FinancialRatiosData> existingRatiosData = financialRatiosRepository.findBySymbol(symbol);

        if (existingRatiosData.isEmpty()) {
            FinancialRatiosData newRatiosData = calculateAndSaveRatios(symbol);
            return Optional.ofNullable(newRatiosData); // Return newly calculated data
        }
        return existingRatiosData;
    }

    public FinancialRatiosData calculateAndSaveRatios(String symbol) {
        Optional<IncomeStatementData> incomeStatementDataOptional = incomeStatementRepository.findBySymbol(symbol);
        Optional<BalanceSheetData> balanceSheetDataOptional = balanceSheetRepository.findBySymbol(symbol);
        Optional<CashFlowData> cashFlowDataOptional = cashFlowRepository.findBySymbol(symbol);
        Optional<SharesOutstandingData> sharesOutstandingDataOptional = sharesOutstandingRepository.findBySymbol(symbol); // Add this

        if (incomeStatementDataOptional.isPresent() && balanceSheetDataOptional.isPresent()
                && cashFlowDataOptional.isPresent() && sharesOutstandingDataOptional.isPresent()) {
            IncomeStatementData incomeStatementData = incomeStatementDataOptional.get();
            BalanceSheetData balanceSheetData = balanceSheetDataOptional.get();
            CashFlowData cashFlowData = cashFlowDataOptional.get();
            SharesOutstandingData sharesOutstandingData = sharesOutstandingDataOptional.get(); // Add this

            FinancialRatiosData financialRatiosData = financialRatiosRepository.findBySymbol(symbol)
                    .orElse(new FinancialRatiosData());
            financialRatiosData.setSymbol(symbol);
            financialRatiosData.setAnnualReports(new ArrayList<>());
            financialRatiosData.setQuarterlyReports(new ArrayList<>());

            // Process Annual Reports
            processAndAddReports(symbol, incomeStatementData.getAnnualReports(), balanceSheetData.getAnnualReports(), cashFlowData.getAnnualReports(), sharesOutstandingData.getData(), financialRatiosData.getAnnualReports()); // Update this
            // Process Quarterly Reports
            processAndAddReports(symbol, incomeStatementData.getQuarterlyReports(), balanceSheetData.getQuarterlyReports(), cashFlowData.getQuarterlyReports(), sharesOutstandingData.getData(), financialRatiosData.getQuarterlyReports()); // Update this

            return financialRatiosRepository.save(financialRatiosData);
        }
        return null; // Or throw an exception if data is not found
    }

    private void processAndAddReports(String symbol,
                                      List<IncomeReport> incomeReports,
                                      List<BalanceSheetReport> balanceSheetReports,
                                      List<CashFlowReport> cashFlowReports,
                                      List<SharesOutstandingReport> sharesOutstandingReports,
                                      List<FinancialRatiosReport> targetList) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        Map<String, BalanceSheetReport> balanceSheetMap = balanceSheetReports.stream()
                .collect(Collectors.toMap(BalanceSheetReport::getFiscalDateEnding, Function.identity(), (a, b) -> a));

        Map<String, CashFlowReport> cashFlowMap = cashFlowReports.stream()
                .collect(Collectors.toMap(CashFlowReport::getFiscalDateEnding, Function.identity(), (a, b) -> a));

        for (IncomeReport incomeReport : incomeReports) {
            String fiscalDateEnding = incomeReport.getFiscalDateEnding();

            // Find corresponding reports
            BalanceSheetReport balanceSheet = balanceSheetMap.get(fiscalDateEnding);
            CashFlowReport cashFlow = cashFlowMap.get(fiscalDateEnding);

            if (balanceSheet == null || cashFlow == null) {
                continue; // Skip if we don't have all required reports
            }

            // Find the most recent shares outstanding report on or before fiscal date
            LocalDate reportFiscalDate = LocalDate.parse(fiscalDateEnding, formatter);
            Optional<SharesOutstandingReport> sharesOutstanding = findLatestSharesOutstanding(
                    sharesOutstandingReports, reportFiscalDate, formatter);

            sharesOutstanding.ifPresent(shares -> {
                FinancialRatiosReport ratios = financialRatiosCalculator.calculateRatios(incomeReport, balanceSheet, cashFlow, shares);
                targetList.add(ratios);
            });
        }
    }

    private Optional<SharesOutstandingReport> findLatestSharesOutstanding(
            List<SharesOutstandingReport> reports,
            LocalDate fiscalDate,
            DateTimeFormatter formatter) {

        return reports.stream()
                .filter(report -> isValidDateOnOrBefore(report.getDate(), fiscalDate, formatter))
                .max(Comparator.comparing(report -> parseDate(report.getDate(), formatter)));
    }

    private boolean isValidDateOnOrBefore(String dateStr, LocalDate fiscalDate, DateTimeFormatter formatter) {
        try {
            LocalDate date = LocalDate.parse(dateStr, formatter);
            return !date.isAfter(fiscalDate);
        } catch (Exception e) {
            return false; // Malformed date
        }
    }

    private LocalDate parseDate(String dateStr, DateTimeFormatter formatter) {
        return LocalDate.parse(dateStr, formatter);
    }

}