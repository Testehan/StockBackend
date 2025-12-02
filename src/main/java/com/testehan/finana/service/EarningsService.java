package com.testehan.finana.service;

import com.testehan.finana.model.filing.CompanyEarningsTranscripts;
import com.testehan.finana.model.EarningsEstimate;
import com.testehan.finana.model.EarningsHistory;
import com.testehan.finana.model.filing.QuarterlyEarningsTranscript;
import com.testehan.finana.repository.CompanyEarningsTranscriptsRepository;
import com.testehan.finana.repository.EarningsEstimatesRepository;
import com.testehan.finana.repository.EarningsHistoryRepository;
import com.testehan.finana.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EarningsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EarningsService.class);

    private final AlphaVantageService alphaVantageService;
    private final FMPService fmpService;
    private final EarningsHistoryRepository earningsHistoryRepository;
    private final CompanyEarningsTranscriptsRepository companyEarningsTranscriptsRepository;
    private final EarningsEstimatesRepository earningsEstimatesRepository;
    private final DateUtils dateUtils;

    public EarningsService(AlphaVantageService alphaVantageService, FMPService fmpService, EarningsHistoryRepository earningsHistoryRepository, CompanyEarningsTranscriptsRepository companyEarningsTranscriptsRepository, EarningsEstimatesRepository earningsEstimatesRepository, DateUtils dateUtils) {
        this.alphaVantageService = alphaVantageService;
        this.fmpService = fmpService;
        this.earningsHistoryRepository = earningsHistoryRepository;
        this.companyEarningsTranscriptsRepository = companyEarningsTranscriptsRepository;
        this.earningsEstimatesRepository = earningsEstimatesRepository;
        this.dateUtils = dateUtils;
    }

    public Mono<QuarterlyEarningsTranscript> getEarningsCallTranscript(String symbol, String quarter) {
        return Mono.defer(() -> {
            Optional<CompanyEarningsTranscripts> earningsCallTranscriptFromDb = companyEarningsTranscriptsRepository.findById(symbol.toUpperCase());
            if (earningsCallTranscriptFromDb.isPresent()) {
                Optional<QuarterlyEarningsTranscript> quarterlyTranscript = earningsCallTranscriptFromDb.get().getTranscripts().stream()
                        .filter(transcript -> {if (Objects.nonNull(transcript.getQuarter())) {
                            return transcript.getQuarter().equals(quarter);
                        } else {
                            return false;
                        }}).findFirst();

                if (quarterlyTranscript.isPresent() && dateUtils.isRecent(quarterlyTranscript.get().getLastUpdated(), DateUtils.CACHE_THREE_MONTHS)) {
                    return Mono.just(quarterlyTranscript.get());
                }
            }

            return alphaVantageService.fetchEarningsCallTranscriptFromApiAndSave(symbol.toUpperCase(), quarter)
                    .flatMap(companyTranscripts -> companyTranscripts.getTranscripts().stream()
                            .filter(transcript -> {if (Objects.nonNull(transcript.getQuarter())) {
                                return transcript.getQuarter().equals(quarter);
                            } else {
                                return false;
                            }})
                            .findFirst()
                            .map(Mono::just)
                            .orElse(Mono.empty()));
        });
    }

    public Mono<QuarterlyEarningsTranscript> getLatestEarningsTranscript(String symbol) {
        return Mono.defer(() -> {
            Optional<CompanyEarningsTranscripts> earningsCallTranscriptFromDb = companyEarningsTranscriptsRepository.findById(symbol.toUpperCase());
            if (earningsCallTranscriptFromDb.isPresent()) {
                List<QuarterlyEarningsTranscript> validTranscripts = earningsCallTranscriptFromDb.get().getTranscripts().stream()
                        .filter(transcript -> transcript.getQuarter() != null 
                                && transcript.getTranscript() != null 
                                && !transcript.getTranscript().isEmpty())
                        .sorted((a, b) -> b.getQuarter().compareTo(a.getQuarter()))
                        .collect(Collectors.toList());
                
                if (!validTranscripts.isEmpty()) {
                    return Mono.just(validTranscripts.get(0));
                }
            }
            return Mono.empty();
        });
    }

    public Mono<List<String>> getAvailableEarningsQuarters(String symbol) {
        return Mono.defer(() -> {
            Optional<CompanyEarningsTranscripts> earningsCallTranscriptFromDb = companyEarningsTranscriptsRepository.findById(symbol.toUpperCase());
            if (earningsCallTranscriptFromDb.isPresent()) {
                List<String> existingQuarters = earningsCallTranscriptFromDb.get().getTranscripts().stream()
                        .map(QuarterlyEarningsTranscript::getQuarter)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());
                
                if (!existingQuarters.isEmpty()) {
                    String latestQuarter = existingQuarters.get(existingQuarters.size() - 1);
                    String currentQuarter = dateUtils.getCurrentQuarter();
                    
                    if (latestQuarter.compareTo(currentQuarter) < 0) {
                        List<String> allQuarters = dateUtils.generateQuartersUpToCurrent(latestQuarter);
                        return Mono.just(allQuarters);
                    }
                }
                
                return Mono.just(existingQuarters);
            }
            return Mono.just(List.of());
        });
    }

    public Mono<EarningsHistory> getEarningsHistory(String symbol) {
        return Mono.defer(() -> {
            Optional<EarningsHistory> earningsHistoryFromDb = earningsHistoryRepository.findBySymbol(symbol.toUpperCase());
            if (earningsHistoryFromDb.isPresent() && dateUtils.isRecent(earningsHistoryFromDb.get().getLastUpdated(), DateUtils.CACHE_ONE_WEEK)) {
                return Mono.just(earningsHistoryFromDb.get());
            } else {
                return fmpService.fetchEarningsHistory(symbol.toUpperCase())
                        .flatMap(earningsHistory -> Mono.just(earningsHistoryRepository.save(earningsHistory)));
            }
        });
    }

    public Mono<EarningsEstimate> getEarningsEstimates(String symbol) {
        return Mono.defer(() -> {
            Optional<EarningsEstimate> earningsEstimateFromDb = earningsEstimatesRepository.findBySymbol(symbol.toUpperCase());
            if (earningsEstimateFromDb.isPresent() && dateUtils.isRecent(earningsEstimateFromDb.get().getLastUpdated(), DateUtils.CACHE_ONE_WEEK)) {
                return Mono.just(earningsEstimateFromDb.get());
            } else {
                return fmpService.fetchAnalystEstimates(symbol.toUpperCase())
                        .flatMap(estimates -> {
                            EarningsEstimate earningsEstimate = new EarningsEstimate();
                            earningsEstimate.setSymbol(symbol.toUpperCase());
                            earningsEstimate.setEstimates(estimates);
                            earningsEstimate.setLastUpdated(LocalDateTime.now());
                            return Mono.just(earningsEstimatesRepository.save(earningsEstimate));
                        });
            }
        });
    }

    public void deleteCompanyEarningsTranscriptsBySymbol(String symbol) {
        companyEarningsTranscriptsRepository.deleteById(symbol);
    }

    public void deleteEarningsHistoryBySymbol(String symbol) {
        earningsHistoryRepository.deleteBySymbol(symbol);
    }

    public void deleteEarningsEstimatesBySymbol(String symbol) {
        earningsEstimatesRepository.deleteBySymbol(symbol);
    }

    public boolean hasEarningsCallTranscript(String symbol) {
        return companyEarningsTranscriptsRepository.findById(symbol).isPresent();
    }

    public boolean hasEarningsHistory(String symbol) {
        return earningsHistoryRepository.findBySymbol(symbol).isPresent();
    }

    public boolean hasEarningsEstimates(String symbol) {
        return earningsEstimatesRepository.findBySymbol(symbol).isPresent();
    }
}
