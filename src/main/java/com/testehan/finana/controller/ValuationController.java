package com.testehan.finana.controller;

import com.testehan.finana.model.valuation.dcf.DcfCalculationData;
import com.testehan.finana.model.valuation.dcf.DcfValuation;
import com.testehan.finana.model.valuation.dcf.ReverseDcfValuation;
import com.testehan.finana.model.valuation.growth.GrowthOutput;
import com.testehan.finana.model.valuation.growth.GrowthValuation;
import com.testehan.finana.service.ValuationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/stock/valuation")
public class ValuationController {

    private static final Logger logger = LoggerFactory.getLogger(ValuationController.class);

    private final ValuationService valuationService;

    public ValuationController(ValuationService valuationService) {
        this.valuationService = valuationService;
    }

    @GetMapping("/dcf/{symbol}")
    public ResponseEntity<DcfCalculationData> getDcfValuationData(@PathVariable String symbol) {
        DcfCalculationData data = valuationService.getDcfCalculationData(symbol.toUpperCase());
        // You might want to add more sophisticated error handling here,
        // e.g., checking if 'data' is null or contains insufficient data
        // and returning appropriate HTTP status codes (e.g., 404 Not Found, 204 No Content).
        if (data == null || data.meta() == null || data.meta().ticker().equals("N/A")) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(data);
    }

    @PostMapping("/dcf")
    public ResponseEntity<Void> saveDcfValuation(@RequestBody DcfValuation dcfValuation) {
        logger.info("Received DCF valuation to save: {}", dcfValuation);
        valuationService.saveDcfValuation(dcfValuation);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reverse-dcf")
    public ResponseEntity<Void> saveReverseDcfValuation(@RequestBody ReverseDcfValuation reverseDcfValuation) {
        logger.info("Received reverse DCF valuation to save: {}", reverseDcfValuation);
        valuationService.saveReverseDcfValuation(reverseDcfValuation);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/reverse-dcf/{symbol}")
    public ResponseEntity<DcfCalculationData> getReverseDcfValuationData(@PathVariable String symbol) {
        DcfCalculationData data = valuationService.getDcfCalculationData(symbol.toUpperCase());
        if (data == null || data.meta() == null || data.meta().ticker().equals("N/A")) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(data);
    }

    @GetMapping("/dcf/history/{symbol}")
    public ResponseEntity<List<DcfValuation>> getDcfHistory(@PathVariable String symbol) {
        return ResponseEntity.ok(valuationService.getDcfHistory(symbol.toUpperCase()));
    }

    @GetMapping("/reverse-dcf/history/{symbol}")
    public ResponseEntity<List<ReverseDcfValuation>> getReverseDcfHistory(@PathVariable String symbol) {
        return ResponseEntity.ok(valuationService.getReverseDcfHistory(symbol.toUpperCase()));
    }

    @GetMapping("/growth/{symbol}")
    public ResponseEntity<GrowthValuation> getGrowthCompanyValuationData(@PathVariable String symbol) {
        GrowthValuation data = valuationService.getGrowthCompanyValuationData(symbol.toUpperCase());
        if (data == null || data.getGrowthValuationData().getTicker() == null ||  data.getGrowthValuationData().getTicker().equals("N/A")) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(data);
    }


    @PostMapping("/calculate/growth")
    public ResponseEntity<GrowthOutput> calculateGrowthCompanyValuation(@RequestBody GrowthValuation growthValuation) {
        logger.info("Received Growth Company valuation to calculate: {}", growthValuation);
        GrowthOutput calculatedOutput = valuationService.calculateGrowthCompanyValuation(growthValuation);
        return ResponseEntity.ok(calculatedOutput);
    }

    @PostMapping("/growth")
    public ResponseEntity<Void> saveGrowthCompanyValuation(@RequestBody GrowthValuation growthValuation) {
        logger.info("Received Growth Company valuation to save: {}", growthValuation);
        valuationService.saveGrowthCompanyValuation(growthValuation);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/growth/history/{symbol}")
    public ResponseEntity<List<GrowthValuation>> getGrowthCompanyValuationHistory(@PathVariable String symbol) {
        return ResponseEntity.ok(valuationService.getGrowthCompanyValuationHistory(symbol.toUpperCase()));
    }

}

    
