package com.testehan.finana.service;

import com.testehan.finana.model.adjustment.FinancialAdjustment;
import com.testehan.finana.model.adjustment.FinancialAdjustmentReport;
import com.testehan.finana.model.finstatement.IncomeReport;
import com.testehan.finana.model.finstatement.IncomeStatementData;
import com.testehan.finana.repository.FinancialAdjustmentRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.util.SafeParser;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AdjustmentServiceImpl implements AdjustmentService {

    private final IncomeStatementRepository incomeStatementRepository;
    private final FinancialAdjustmentRepository financialAdjustmentRepository;
    private final SafeParser safeParser;

    public AdjustmentServiceImpl(IncomeStatementRepository incomeStatementRepository, FinancialAdjustmentRepository financialAdjustmentRepository, SafeParser safeParser) {
        this.incomeStatementRepository = incomeStatementRepository;
        this.financialAdjustmentRepository = financialAdjustmentRepository;
        this.safeParser = safeParser;
    }

    @Override
    public FinancialAdjustment getFinancialAdjustments(String symbol) {
        Optional<FinancialAdjustment> existingAdjustment = financialAdjustmentRepository.findBySymbol(symbol);
        if (existingAdjustment.isPresent()) {
            return existingAdjustment.get();
        }

        Optional<IncomeStatementData> incomeStatementDataOptional = incomeStatementRepository.findBySymbol(symbol);
        if (incomeStatementDataOptional.isEmpty() || incomeStatementDataOptional.get().getAnnualReports() == null) {
            return new FinancialAdjustment();
        }

        List<IncomeReport> annualReports = incomeStatementDataOptional.get().getAnnualReports();

        if (annualReports.size() < 5) {
            // Not enough data to calculate
            return new FinancialAdjustment();
        }

        // Sort by date (assuming YYYY-MM-DD format) to get the latest 5 years
        List<IncomeReport> last5YearsOfIncomeReports = annualReports.stream()
                .sorted(Comparator.comparing(IncomeReport::getDate).reversed())
                .limit(5)
                .collect(Collectors.toList());

        FinancialAdjustmentReport adjustmentReport = calculateRdAdjustment(last5YearsOfIncomeReports);

        FinancialAdjustment financialAdjustment = new FinancialAdjustment();
        financialAdjustment.setSymbol(symbol);
        financialAdjustment.setLastUpdated(LocalDateTime.now());
        financialAdjustment.setAnnualAdjustments(List.of(adjustmentReport));

        return financialAdjustmentRepository.save(financialAdjustment);
    }

    @Override
    public void deleteFinancialAdjustmentBySymbol(String symbol) {
        financialAdjustmentRepository.findBySymbol(symbol).ifPresent(financialAdjustmentRepository::delete);
    }

    private FinancialAdjustmentReport calculateRdAdjustment(List<IncomeReport> incomeReports) {
        FinancialAdjustmentReport report = new FinancialAdjustmentReport();

        // Ensure we have exactly 5 years, sorted in descending order (latest first)
        if (incomeReports.size() < 5) {
            return report;
        }

        // Extract years for easier access, ensuring latest year is index 0
        IncomeReport year0 = incomeReports.get(0); // Current year
        IncomeReport year_1 = incomeReports.get(1);
        IncomeReport year_2 = incomeReports.get(2);
        IncomeReport year_3 = incomeReports.get(3);
        IncomeReport year_4 = incomeReports.get(4); // Oldest year

        BigDecimal rd0 = safeParser.parse(year0.getResearchAndDevelopmentExpenses());
        BigDecimal rd_1 = safeParser.parse(year_1.getResearchAndDevelopmentExpenses());
        BigDecimal rd_2 = safeParser.parse(year_2.getResearchAndDevelopmentExpenses());
        BigDecimal rd_3 = safeParser.parse(year_3.getResearchAndDevelopmentExpenses());
        BigDecimal rd_4 = safeParser.parse(year_4.getResearchAndDevelopmentExpenses());

        BigDecimal ebit0 = safeParser.parse(year0.getOperatingIncome());

        // Check if R&D for latest year is valid and > 0
        if (rd0.compareTo(BigDecimal.ZERO) <= 0 || ebit0.compareTo(BigDecimal.ZERO) == 0) {
            return report;
        }

        report.setCalendarYear(Integer.parseInt(year0.getDate().substring(0, 4))); // Assuming date is "YYYY-MM-DD"
        report.setDate(year0.getDate());
        report.setReportedOperatingIncome(ebit0.toString());

        // Calculate Research Asset
        BigDecimal researchAsset = rd0.multiply(BigDecimal.valueOf(1.0))
                .add(rd_1.multiply(BigDecimal.valueOf(0.8)))
                .add(rd_2.multiply(BigDecimal.valueOf(0.6)))
                .add(rd_3.multiply(BigDecimal.valueOf(0.4)))
                .add(rd_4.multiply(BigDecimal.valueOf(0.2)));
        report.setResearchAsset(researchAsset.toString());

        // Calculate Adjustments
        // The user specified R&D_-4 as amortization.
        BigDecimal rdAmortization = rd_4;
        BigDecimal currentRd = rd0;

        BigDecimal rdCapitalizationAdjustment = currentRd.subtract(rdAmortization);
        BigDecimal adjustedOperatingIncome = ebit0.add(rdCapitalizationAdjustment);

        report.setAddBackCurrentRd(currentRd.toString());
        report.setSubtractRdAmortization(rdAmortization.toString());
        report.setRdCapitalizationAdjustment(rdCapitalizationAdjustment.toString());
        report.setAdjustedOperatingIncome(adjustedOperatingIncome.toString());

        return report;
    }
}
