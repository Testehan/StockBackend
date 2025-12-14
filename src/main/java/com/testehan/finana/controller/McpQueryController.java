package com.testehan.finana.controller;

import com.testehan.finana.service.StockQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mcp")
public class McpQueryController {

    private final StockQueryService stockQueryService;

    public McpQueryController(StockQueryService stockQueryService) {
        this.stockQueryService = stockQueryService;
    }

    @PostMapping("/query")
    public ResponseEntity<String> queryStock(@RequestBody String question) {
        String answer = stockQueryService.queryStock(question);
        return ResponseEntity.ok(answer);
    }
}
