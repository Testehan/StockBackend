package com.testehan.finana.controller;

import com.testehan.finana.service.DeepResearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/research")
public class DeepResearchController {

    private final DeepResearchService deepResearchService;

    private static final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads/research/";
    private static final Map<String, byte[]> documentStorage = new ConcurrentHashMap<>();

    public DeepResearchController(DeepResearchService deepResearchService) {
        this.deepResearchService = deepResearchService;
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    @GetMapping("/{stockTicker}")
    public Mono<ResponseEntity<?>> getResearch(@PathVariable String stockTicker) {
        return deepResearchService.getNewsReport(stockTicker.toUpperCase())
                .map(report -> {
                    if (report != null) {
                        return ResponseEntity.ok(report);
                    } else {
                        return ResponseEntity.ok().body("No deep research report found for " + stockTicker.toUpperCase());
                    }
                });
    }

    @PostMapping("/{stockTicker}/document")
    public Mono<ResponseEntity<String>> uploadDocument(
            @PathVariable String stockTicker,
            @RequestParam("file") MultipartFile file) {
        
        if (file.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body("File is empty"));
        }

        String filename = stockTicker.toUpperCase() + "_" + System.currentTimeMillis() + ".pdf";
        
        return Mono.fromCallable(() -> {
                try {
                    Path filePath = Paths.get(UPLOAD_DIR + filename);
                    Files.write(filePath, file.getBytes());
                    
                    documentStorage.put(filename, file.getBytes());
                } catch (IOException e) {
                    throw new RuntimeException("Failed to save document", e);
                }
                return file.getBytes();
        })
        .doOnNext(bytes -> deepResearchService.processDocument(bytes, stockTicker.toUpperCase())
                .subscribe())
        .then(Mono.just(ResponseEntity.ok("Document uploaded and processing started")));
    }
}
