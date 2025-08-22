package com.testehan.finana.service;

import com.testehan.finana.model.*;
import com.testehan.finana.repository.BalanceSheetRepository;
import com.testehan.finana.repository.FinancialRatiosRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class FinancialMetricsService {

    private final IncomeStatementRepository incomeStatementRepository;
    private final BalanceSheetRepository balanceSheetRepository;
    private final FinancialRatiosRepository financialRatiosRepository;

    @Autowired
    public FinancialMetricsService(IncomeStatementRepository incomeStatementRepository,
                                   BalanceSheetRepository balanceSheetRepository,
                                   FinancialRatiosRepository financialRatiosRepository) {
        this.incomeStatementRepository = incomeStatementRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.financialRatiosRepository = financialRatiosRepository;
    }

    public void calculateAndSaveRatios(String symbol) {
        Optional<IncomeStatementData> incomeStatementDataOptional = incomeStatementRepository.findBySymbol(symbol);
        Optional<BalanceSheetData> balanceSheetDataOptional = balanceSheetRepository.findBySymbol(symbol);

        if (incomeStatementDataOptional.isPresent() && balanceSheetDataOptional.isPresent()) {
            IncomeStatementData incomeStatementData = incomeStatementDataOptional.get();
            BalanceSheetData balanceSheetData = balanceSheetDataOptional.get();

            processReports(symbol, incomeStatementData.getAnnualReports(), balanceSheetData.getAnnualReports());
            processReports(symbol, incomeStatementData.getQuarterlyReports(), balanceSheetData.getQuarterlyReports());
        }
    }

    private void processReports(String symbol, List<IncomeReport> incomeReports, List<BalanceSheetReport> balanceSheetReports) {
        for (IncomeReport incomeReport : incomeReports) {
            balanceSheetReports.stream()
                    .filter(balanceSheetReport -> incomeReport.getFiscalDateEnding().equals(balanceSheetReport.getFiscalDateEnding()))
                    .findFirst()
                    .ifPresent(balanceSheetReport -> {
                        FinancialRatios financialRatios = calculateRatios(symbol, incomeReport, balanceSheetReport);
                        financialRatiosRepository.save(financialRatios);
                    });
        }
    }

    public FinancialRatios calculateRatios(String symbol, IncomeReport incomeReport, BalanceSheetReport balanceSheetReport) {
        FinancialRatios ratios = new FinancialRatios();
        ratios.setSymbol(symbol);
        ratios.setFiscalDateEnding(incomeReport.getFiscalDateEnding());

        SafeParser safeParser = new SafeParser();

        BigDecimal grossProfit = safeParser.parse(incomeReport.getGrossProfit());
        BigDecimal totalRevenue = safeParser.parse(incomeReport.getTotalRevenue());
        if (totalRevenue.compareTo(BigDecimal.ZERO) != 0) {
            ratios.setGrossProfitMargin(grossProfit.divide(totalRevenue, 4, RoundingMode.HALF_UP));
        }

        BigDecimal netIncome = safeParser.parse(incomeReport.getNetIncome());
        if (totalRevenue.compareTo(BigDecimal.ZERO) != 0) {
            ratios.setNetProfitMargin(netIncome.divide(totalRevenue, 4, RoundingMode.HALF_UP));
        }

        BigDecimal totalAssets = safeParser.parse(balanceSheetReport.getTotalAssets());
        if (totalAssets.compareTo(BigDecimal.ZERO) != 0) {
            ratios.setReturnOnAssets(netIncome.divide(totalAssets, 4, RoundingMode.HALF_UP));
        }

        BigDecimal totalShareholderEquity = safeParser.parse(balanceSheetReport.getTotalShareholderEquity());
        if (totalShareholderEquity.compareTo(BigDecimal.ZERO) != 0) {
            ratios.setReturnOnEquity(netIncome.divide(totalShareholderEquity, 4, RoundingMode.HALF_UP));
        }

        BigDecimal totalCurrentAssets = safeParser.parse(balanceSheetReport.getTotalCurrentAssets());
        BigDecimal totalCurrentLiabilities = safeParser.parse(balanceSheetReport.getOtherCurrentLiabilities());
        if (totalCurrentLiabilities.compareTo(BigDecimal.ZERO) != 0) {
            ratios.setCurrentRatio(totalCurrentAssets.divide(totalCurrentLiabilities, 4, RoundingMode.HALF_UP));
        }

        BigDecimal inventory = safeParser.parse(balanceSheetReport.getInventory());
        BigDecimal quickAssets = totalCurrentAssets.subtract(inventory);
        if (totalCurrentLiabilities.compareTo(BigDecimal.ZERO) != 0) {
            ratios.setQuickRatio(quickAssets.divide(totalCurrentLiabilities, 4, RoundingMode.HALF_UP));
        }

        BigDecimal totalLiabilities = safeParser.parse(balanceSheetReport.getTotalLiabilities());
        if (totalAssets.compareTo(BigDecimal.ZERO) != 0) {
            ratios.setDebtToAssetsRatio(totalLiabilities.divide(totalAssets, 4, RoundingMode.HALF_UP));
        }

        if (totalShareholderEquity.compareTo(BigDecimal.ZERO) != 0) {
            ratios.setDebtToEquityRatio(totalLiabilities.divide(totalShareholderEquity, 4, RoundingMode.HALF_UP));
        }

        if (totalAssets.compareTo(BigDecimal.ZERO) != 0) {
            ratios.setAssetTurnover(totalRevenue.divide(totalAssets, 4, RoundingMode.HALF_UP));
        }

        BigDecimal costOfRevenue = safeParser.parse(incomeReport.getCostOfRevenue());
        if (inventory.compareTo(BigDecimal.ZERO) != 0) {
            ratios.setInventoryTurnover(costOfRevenue.divide(inventory, 4, RoundingMode.HALF_UP));
        }

        return ratios;
    }

    public List<FinancialRatios> getFinancialRatios(String symbol) {
        List<FinancialRatios> existingRatios = financialRatiosRepository.findBySymbol(symbol);

        if (existingRatios.isEmpty()) {
            calculateAndSaveRatios(symbol);
            // After calculation, retrieve them again to return
            existingRatios = financialRatiosRepository.findBySymbol(symbol);
        }
        return existingRatios;
    }

    private static class SafeParser {
        public BigDecimal parse(String value) {
            if (value == null || value.isEmpty() || value.equals("None")) {
                return BigDecimal.ZERO;
            }
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }
    }
}
