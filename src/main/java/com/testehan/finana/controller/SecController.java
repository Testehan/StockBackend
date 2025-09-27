package com.testehan.finana.controller;

import com.testehan.finana.model.SecFiling;
import com.testehan.finana.model.TenKFilings;
import com.testehan.finana.model.TenQFilings;
import com.testehan.finana.repository.SecFilingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.Optional;

@RestController
@RequestMapping("/stocks/sec")
public class SecController {

    private final SecFilingRepository secFilingRepository;

    @Autowired
    public SecController(SecFilingRepository secFilingRepository) {
        this.secFilingRepository = secFilingRepository;
    }

    @GetMapping("/10-k/{ticker}/risk-factors")
    public Mono<String> getRiskFactorsFrom10K(@PathVariable String ticker) {
        return Mono.fromCallable(() -> {
            Optional<SecFiling> secFiling = secFilingRepository.findById(ticker);
            if (secFiling.isPresent() && secFiling.get().getTenKFilings() != null) {
                return secFiling.get().getTenKFilings().stream()
                        .max(Comparator.comparing(TenKFilings::getFiledAt))
                        .map(TenKFilings::getRiskFactors)
                        .orElse(null);
            }
            return null;
        });
    }

    @GetMapping("/10-k/{ticker}/management-discussion")
    public Mono<String> getManagementDiscussionFrom10K(@PathVariable String ticker) {
        return Mono.fromCallable(() -> {
            Optional<SecFiling> secFiling = secFilingRepository.findById(ticker);
            if (secFiling.isPresent() && secFiling.get().getTenKFilings() != null) {
                return secFiling.get().getTenKFilings().stream()
                        .max(Comparator.comparing(TenKFilings::getFiledAt))
                        .map(TenKFilings::getManagementDiscussion)
                        .orElse(null);
            }
            return null;
        });
    }

    @GetMapping("/10-k/{ticker}/business-description")
    public Mono<String> getBusinessDescriptionFrom10K(@PathVariable String ticker) {
        return Mono.fromCallable(() -> {
            Optional<SecFiling> secFiling = secFilingRepository.findById(ticker);
            if (secFiling.isPresent() && secFiling.get().getTenKFilings() != null) {
                return secFiling.get().getTenKFilings().stream()
                        .max(Comparator.comparing(TenKFilings::getFiledAt))
                        .map(TenKFilings::getBusinessDescription)
                        .orElse(null);
            }
            return null;
        });
    }

    @GetMapping("/10-q/{ticker}/management-discussion")
    public Mono<String> getManagementDiscussionFrom10Q(@PathVariable String ticker) {
        return Mono.fromCallable(() -> {
            Optional<SecFiling> secFiling = secFilingRepository.findById(ticker);
            if (secFiling.isPresent() && secFiling.get().getTenQFilings() != null) {
                return secFiling.get().getTenQFilings().stream()
                        .max(Comparator.comparing(TenQFilings::getFiledAt))
                        .map(TenQFilings::getManagementDiscussion)
                        .orElse(null);
            }
            return null;
        });
    }

    @GetMapping("/10-q/{ticker}/risk-factors")
    public Mono<String> getRiskFactorsFrom10Q(@PathVariable String ticker) {
        return Mono.fromCallable(() -> {
            Optional<SecFiling> secFiling = secFilingRepository.findById(ticker);
            if (secFiling.isPresent() && secFiling.get().getTenQFilings() != null) {
                return secFiling.get().getTenQFilings().stream()
                        .max(Comparator.comparing(TenQFilings::getFiledAt))
                        .map(TenQFilings::getRiskFactors)
                        .orElse(null);
            }
            return null;
        });
    }
}
