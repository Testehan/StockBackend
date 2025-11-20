package com.testehan.finana.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.repository.CompanyOverviewRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class CompanyDataService {
    private final FMPService fmpService;
    private final CompanyOverviewRepository companyOverviewRepository;

    public CompanyDataService(FMPService fmpService, CompanyOverviewRepository companyOverviewRepository) {
        this.fmpService = fmpService;
        this.companyOverviewRepository = companyOverviewRepository;
    }

    public Mono<List<CompanyOverview>> getCompanyOverview(String symbol) {
        return Mono.fromCallable(() -> companyOverviewRepository.findBySymbol(symbol.toUpperCase()))
                .flatMap(opt -> {
                    if (opt.isPresent() && isRecent(opt.get().getLastUpdated(), 10080)) {
                        return Mono.just(List.of(opt.get()));
                    }
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() ->
                    Mono.fromCallable(() -> companyOverviewRepository.findBySymbol(symbol.toUpperCase()))
                            .flatMap(existingOpt -> 
                                fmpService.getCompanyOverview(symbol.toUpperCase(), existingOpt)
                                        .flatMap(overview -> 
                                            Mono.fromCallable(() -> companyOverviewRepository.save(overview))
                                                .map(saved -> List.of(saved))
                                        )
                            )
                ))
                .onErrorResume(DuplicateKeyException.class, e ->
                    Mono.fromCallable(() -> companyOverviewRepository.findBySymbol(symbol.toUpperCase()))
                            .flatMap(opt -> opt.map(overview -> Mono.just(List.of(overview))).orElseGet(Mono::empty))
                );
    }

    private boolean isRecent(LocalDateTime lastUpdated, int minutes) {
        if (lastUpdated == null) {
            return false;
        }
        return ChronoUnit.MINUTES.between(lastUpdated, LocalDateTime.now()) < minutes;
    }

    public Page<CompanyOverview> findAllCompanyOverview(Pageable pageable) {
        return companyOverviewRepository.findAll(pageable);
    }

    public List<CompanyOverview> findBySymbolsIn(List<String> symbols) {
        return companyOverviewRepository.findBySymbolIn(symbols);
    }

    public void deleteBySymbol(String symbol) {
        companyOverviewRepository.deleteBySymbol(symbol);
    }

    public boolean hasCompanyOverview(String symbol) {
        return companyOverviewRepository.findBySymbol(symbol).isPresent();
    }
}