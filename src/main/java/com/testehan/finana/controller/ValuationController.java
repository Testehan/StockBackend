package com.testehan.finana.controller;

import com.testehan.finana.model.valuation.dcf.DcfCalculationData;
import com.testehan.finana.model.valuation.dcf.DcfOutput;
import com.testehan.finana.model.valuation.dcf.DcfValuation;
import com.testehan.finana.model.valuation.dcf.ReverseDcfOutput;
import com.testehan.finana.model.valuation.dcf.ReverseDcfValuation;
import com.testehan.finana.model.valuation.growth.GrowthOutput;
import com.testehan.finana.model.valuation.growth.GrowthUserInputLlmResponse;
import com.testehan.finana.model.valuation.growth.GrowthValuation;
import com.testehan.finana.service.ValuationAlertService;
import com.testehan.finana.service.ValuationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/stocks/valuation")
public class ValuationController {

    private static final Logger logger = LoggerFactory.getLogger(ValuationController.class);

    private final ValuationService valuationService;
    private final ValuationAlertService valuationAlertService;
    private final JwtDecoder jwtDecoder;

    public ValuationController(ValuationService valuationService, 
                              ValuationAlertService valuationAlertService,
                              JwtDecoder jwtDecoder) {
        this.valuationService = valuationService;
        this.valuationAlertService = valuationAlertService;
        this.jwtDecoder = jwtDecoder;
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

    @DeleteMapping("/dcf/{symbol}")
    public ResponseEntity<Void> deleteDcfValuation(
            @PathVariable String symbol,
            @RequestParam String valuationDate) {
        logger.info("Received request to delete DCF valuation for {} with date: {}", symbol, valuationDate);
        boolean deleted = valuationService.deleteDcfValuation(symbol.toUpperCase(), valuationDate);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/reverse-dcf/history/{symbol}")
    public ResponseEntity<List<ReverseDcfValuation>> getReverseDcfHistory(@PathVariable String symbol) {
        return ResponseEntity.ok(valuationService.getReverseDcfHistory(symbol.toUpperCase()));
    }

    @DeleteMapping("/reverse-dcf/{symbol}")
    public ResponseEntity<Void> deleteReverseDcfValuation(
            @PathVariable String symbol,
            @RequestParam String valuationDate) {
        logger.info("Received request to delete Reverse DCF valuation for {} with date: {}", symbol, valuationDate);
        boolean deleted = valuationService.deleteReverseDcfValuation(symbol.toUpperCase(), valuationDate);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/growth/{symbol}")
    public ResponseEntity<GrowthValuation> getGrowthCompanyValuationData(@PathVariable String symbol) {
        GrowthValuation data = valuationService.getGrowthCompanyValuationData(symbol.toUpperCase());
        if (data == null || data.getGrowthValuationData().getTicker() == null ||  data.getGrowthValuationData().getTicker().equals("N/A")) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(data);
    }


    @GetMapping("/growth/recommendation/{symbol}/{scenario}")
    public ResponseEntity<GrowthUserInputLlmResponse> getGrowthValuationLlmRecommendation(@PathVariable String symbol, @PathVariable String scenario) {
        return ResponseEntity.ok(valuationService.getGrowthValuationLlmRecommendation(symbol.toUpperCase(), scenario));
    }

    @PostMapping("/calculate/growth")
    public ResponseEntity<GrowthOutput> calculateGrowthCompanyValuation(@RequestBody GrowthValuation growthValuation) {
        logger.info("Received Growth Company valuation to calculate: {}", growthValuation);
        GrowthOutput calculatedOutput = valuationService.calculateGrowthCompanyValuation(growthValuation);
        return ResponseEntity.ok(calculatedOutput);
    }

    @PostMapping("/calculate/dcf")
    public ResponseEntity<DcfOutput> calculateDcfValuation(@RequestBody DcfValuation dcfValuation) {
        logger.info("Received DCF valuation to calculate: {}", dcfValuation);
        DcfOutput calculatedOutput = valuationService.calculateDcfValuation(dcfValuation);
        return ResponseEntity.ok(calculatedOutput);
    }

    @PostMapping("/calculate/reverse-dcf")
    public ResponseEntity<ReverseDcfOutput> calculateReverseDcfValuation(@RequestBody ReverseDcfValuation reverseDcfValuation) {
        logger.info("Received Reverse DCF valuation to calculate: {}", reverseDcfValuation);
        ReverseDcfOutput calculatedOutput = valuationService.calculateReverseDcfValuation(reverseDcfValuation);
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

    @DeleteMapping("/growth/{symbol}")
    public ResponseEntity<Void> deleteGrowthValuation(
            @PathVariable String symbol,
            @RequestParam String valuationDate) {
        logger.info("Received request to delete Growth valuation for {} with date: {}", symbol, valuationDate);
        boolean deleted = valuationService.deleteGrowthValuation(symbol.toUpperCase(), valuationDate);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/alerts-stream/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToAlerts(
            @PathVariable String userId,
            @RequestParam String userEmail,
            @RequestParam String token) {
        
        Jwt jwt = jwtDecoder.decode(token);
        String tokenEmail = jwt.getClaimAsString("email");
        if (tokenEmail == null || !userEmail.equals(tokenEmail)) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized");
        }
        
        return valuationAlertService.subscribe(userEmail);
    }

}

    
