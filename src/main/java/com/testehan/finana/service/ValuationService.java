package com.testehan.finana.service;

import com.testehan.finana.model.valuation.dcf.DcfCalculationData;
import com.testehan.finana.model.valuation.dcf.DcfOutput;
import com.testehan.finana.model.valuation.dcf.DcfValuation;
import com.testehan.finana.model.valuation.dcf.ReverseDcfOutput;
import com.testehan.finana.model.valuation.dcf.ReverseDcfValuation;
import com.testehan.finana.model.valuation.growth.GrowthOutput;
import com.testehan.finana.model.valuation.growth.GrowthValuation;
import com.testehan.finana.service.valuation.DcfValuationService;
import com.testehan.finana.service.valuation.GrowthValuationService;
import com.testehan.finana.service.valuation.ReverseDcfValuationService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Facade service that delegates to specific valuation services.
 * This service provides a unified interface for all valuation operations.
 */
@Service
public class ValuationService {

    private final GrowthValuationService growthValuationService;
    private final DcfValuationService dcfValuationService;
    private final ReverseDcfValuationService reverseDcfValuationService;

    public ValuationService(GrowthValuationService growthValuationService,
                            DcfValuationService dcfValuationService,
                            ReverseDcfValuationService reverseDcfValuationService) {
        this.growthValuationService = growthValuationService;
        this.dcfValuationService = dcfValuationService;
        this.reverseDcfValuationService = reverseDcfValuationService;
    }

    // Growth Valuation Methods
    public GrowthValuation getGrowthCompanyValuationData(String ticker) {
        return growthValuationService.getGrowthCompanyValuationData(ticker);
    }

    public GrowthOutput calculateGrowthCompanyValuation(GrowthValuation growthValuation) {
        return growthValuationService.calculateGrowthCompanyValuation(growthValuation);
    }

    public void saveGrowthCompanyValuation(GrowthValuation growthValuation) {
        growthValuationService.saveGrowthCompanyValuation(growthValuation);
    }

    public List<GrowthValuation> getGrowthCompanyValuationHistory(String ticker) {
        return growthValuationService.getGrowthCompanyValuationHistory(ticker);
    }

    // DCF Valuation Methods
    public DcfCalculationData getDcfCalculationData(String ticker) {
        return dcfValuationService.getDcfCalculationData(ticker);
    }

    public DcfOutput calculateDcfValuation(DcfValuation dcfValuation) {
        return dcfValuationService.calculateDcfValuation(
            dcfValuation.getDcfCalculationData(),
            dcfValuation.getDcfUserInput()
        );
    }

    public void saveDcfValuation(DcfValuation dcfValuation) {
        dcfValuationService.saveDcfValuation(dcfValuation);
    }

    public List<DcfValuation> getDcfHistory(String ticker) {
        return dcfValuationService.getDcfHistory(ticker);
    }

    // Reverse DCF Valuation Methods
    public ReverseDcfOutput calculateReverseDcfValuation(ReverseDcfValuation reverseDcfValuation) {
        return reverseDcfValuationService.calculateReverseDcfValuation(
            reverseDcfValuation.getDcfCalculationData(),
            reverseDcfValuation.getReverseDcfUserInput()
        );
    }

    public void saveReverseDcfValuation(ReverseDcfValuation reverseDcfValuation) {
        reverseDcfValuationService.saveReverseDcfValuation(reverseDcfValuation);
    }

    public List<ReverseDcfValuation> getReverseDcfHistory(String ticker) {
        return reverseDcfValuationService.getReverseDcfHistory(ticker);
    }
}
