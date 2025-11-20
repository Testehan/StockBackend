package com.testehan.finana.util.ratio;

import com.testehan.finana.model.ratio.FinancialRatiosReport;
import com.testehan.finana.util.data.ParsedFinancialData;

/**
 * Interface for all financial ratio calculators.
 * Each calculator is responsible for calculating a specific category of financial ratios.
 */
public interface RatioCalculator {
    
    /**
     * Calculates ratios and sets them on the provided FinancialRatiosReport.
     * 
     * @param ratios The report to populate with calculated ratios
     * @param data The parsed financial data used for calculations
     */
    void calculate(FinancialRatiosReport ratios, ParsedFinancialData data);
    
    /**
     * Returns the name of this calculator category.
     * Used for logging and debugging purposes.
     * 
     * @return The category name
     */
    String getCategoryName();
}
