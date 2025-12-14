package com.testehan.finana.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Service
public class CompanyDataService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompanyDataService.class);
    
    private final FMPService fmpService;
    private final CompanyOverviewRepository companyOverviewRepository;
    private final DateUtils dateUtils;

    public CompanyDataService(FMPService fmpService, CompanyOverviewRepository companyOverviewRepository, DateUtils dateUtils) {
        this.fmpService = fmpService;
        this.companyOverviewRepository = companyOverviewRepository;
        this.dateUtils = dateUtils;
    }

    public Mono<List<CompanyOverview>> getCompanyOverview(String symbol) {
        return Mono.defer(() -> 
            Mono.fromCallable(() -> companyOverviewRepository.findBySymbol(symbol.toUpperCase()))
        ).flatMap(existingOpt -> {
            if (existingOpt.isPresent() && dateUtils.isRecent(existingOpt.get().getLastUpdated(), DateUtils.CACHE_ONE_WEEK)) {
                return Mono.just(List.of(existingOpt.get()));
            }
            
            // Try to refresh from API
            return fmpService.getCompanyOverview(symbol.toUpperCase(), existingOpt)
                    .flatMap(overview -> 
                        Mono.fromCallable(() -> companyOverviewRepository.save(overview))
                            .map(saved -> List.of(saved))
                    )
                    .onErrorResume(e -> {
                        if (existingOpt.isPresent()) {
                            LOGGER.warn("API call failed for company overview of {}. Falling back to cached data from {}.", 
                                        symbol, existingOpt.get().getLastUpdated());
                            return Mono.just(List.of(existingOpt.get()));
                        }
                        LOGGER.error("API call failed for company overview of {} and no cached data available.", symbol);
                        return Mono.error(e);
                    });
        }).switchIfEmpty(Mono.defer(() -> {
            // No existing data, try to fetch from API
            return fmpService.getCompanyOverview(symbol.toUpperCase(), Optional.empty())
                    .flatMap(overview -> 
                        Mono.fromCallable(() -> companyOverviewRepository.save(overview))
                            .map(saved -> List.of(saved))
                    )
                    .onErrorResume(e -> {
                        LOGGER.error("API call failed for company overview of {} and no cached data available.", symbol);
                        return Mono.error(e);
                    });
        }));
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