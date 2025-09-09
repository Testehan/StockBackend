package com.testehan.finana.service;

import com.testehan.finana.model.*;
import com.testehan.finana.repository.FinancialRatiosRepository;
import com.testehan.finana.util.FinancialRatiosCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FinancialMetricsService {

    private final FinancialDataService financialDataService;
    private final FinancialRatiosRepository financialRatiosRepository;

    private final FinancialRatiosCalculator financialRatiosCalculator;

    @Autowired
    public FinancialMetricsService(FinancialDataService financialDataService,
                                   FinancialRatiosRepository financialRatiosRepository, FinancialRatiosCalculator financialRatiosCalculator) {
        this.financialDataService = financialDataService;
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
        CompanyOverview companyOverview = financialDataService.getCompanyOverview(symbol).block().getFirst();
        Optional<IncomeStatementData> incomeStatementDataOptional = financialDataService.getIncomeStatements(symbol).blockOptional();
        Optional<BalanceSheetData> balanceSheetDataOptional = financialDataService.getBalanceSheet(symbol).blockOptional();
        Optional<CashFlowData> cashFlowDataOptional = financialDataService.getCashFlow(symbol).blockOptional();
        Optional<SharesOutstandingData> sharesOutstandingDataOptional = financialDataService.getSharesOutstanding(symbol).blockOptional();

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
            processAndAddReports(symbol, companyOverview, incomeStatementData.getAnnualReports(), balanceSheetData.getAnnualReports(), cashFlowData.getAnnualReports(), sharesOutstandingData.getData(), financialRatiosData.getAnnualReports()); // Update this
            // Process Quarterly Reports
            processAndAddReports(symbol, companyOverview, incomeStatementData.getQuarterlyReports(), balanceSheetData.getQuarterlyReports(), cashFlowData.getQuarterlyReports(), sharesOutstandingData.getData(), financialRatiosData.getQuarterlyReports()); // Update this

            return financialRatiosRepository.save(financialRatiosData);
        }
        return null; // Or throw an exception if data is not found
    }

    private void processAndAddReports(String symbol,
                                      CompanyOverview companyOverview,
                                      List<IncomeReport> incomeReports,
                                      List<BalanceSheetReport> balanceSheetReports,
                                      List<CashFlowReport> cashFlowReports,
                                      List<SharesOutstandingReport> sharesOutstandingReports,
                                      List<FinancialRatiosReport> targetList) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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

            FinancialRatiosReport ratios = financialRatiosCalculator.calculateRatios(companyOverview, incomeReport, balanceSheet, cashFlow);
            targetList.add(ratios);

        }
    }

}