package com.testehan.finana.util.data;

import com.testehan.finana.model.finstatement.BalanceSheetReport;
import com.testehan.finana.model.finstatement.CashFlowReport;
import com.testehan.finana.model.finstatement.IncomeReport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class FmpDataCleaner {

    /**
     * Cleans Income Statements using Revenue and Net Income as the signature.
     */
    public static List<IncomeReport> cleanIncomeStatements(List<IncomeReport> reports) {
        return deduplicate(
                reports,
                report -> report.getDate(),
                report -> report.getFiscalYear() + "_" + report.getPeriod(),
                report -> report.getRevenue() + "_" + report.getNetIncome() // Financial Signature
        );
    }

    /**
     * Cleans Balance Sheets using Total Assets and Total Liabilities as the signature.
     */
    public static List<BalanceSheetReport> cleanBalanceSheets(List<BalanceSheetReport> reports) {
        return deduplicate(
                reports,
                report -> report.getDate(),
                report -> report.getFiscalYear() + "_" + report.getPeriod(),
                report -> report.getTotalAssets() + "_" + report.getTotalLiabilities() // Financial Signature
        );
    }

    /**
     * Cleans Cash Flow Statements using Operating Cash Flow and Free Cash Flow as the signature.
     */
    public static List<CashFlowReport> cleanCashFlows(List<CashFlowReport> reports) {
        return deduplicate(
                reports,
                report -> report.getDate(),
                report -> report.getFiscalYear() + "_" + report.getPeriod(),
                report -> report.getOperatingCashFlow() + "_" + report.getFreeCashFlow() // Financial Signature
        );
    }

    /**
     * PRIVATE GENERIC HELPER: Does the actual heavy lifting for all statement types.
     */
    private static <T> List<T> deduplicate(
            List<T> reports,
            Function<T, String> dateExtractor,
            Function<T, String> periodKeyExtractor,
            Function<T, String> signatureExtractor) {

        // 1. Sort descending by Date
        reports.sort((a, b) -> {
            String dateA = dateExtractor.apply(a) != null ? dateExtractor.apply(a) : "";
            String dateB = dateExtractor.apply(b) != null ? dateExtractor.apply(b) : "";
            return dateB.compareTo(dateA);
        });

        Set<String> seenFinancials = new HashSet<>();
        Set<String> seenPeriods = new HashSet<>();
        List<T> cleanedReports = new ArrayList<>();

        // 2. Filter duplicates
        for (T report : reports) {
            String financialSignature = signatureExtractor.apply(report);
            String periodKey = periodKeyExtractor.apply(report);

            if (!seenFinancials.contains(financialSignature) && !seenPeriods.contains(periodKey)) {
                cleanedReports.add(report);
                seenFinancials.add(financialSignature);
                seenPeriods.add(periodKey);
            }
        }

        return cleanedReports;
    }
}