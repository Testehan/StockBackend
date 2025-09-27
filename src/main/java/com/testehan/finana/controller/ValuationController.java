package com.testehan.finana.controller;

import com.testehan.finana.model.DcfCalculationData;
import com.testehan.finana.service.ValuationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stock/valuation")
public class ValuationController {

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
}
