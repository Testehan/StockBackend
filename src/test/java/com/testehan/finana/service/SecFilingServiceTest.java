package com.testehan.finana.service;

import com.testehan.finana.model.filing.SecFilingUrlData;
import com.testehan.finana.model.filing.SecFilingsUrls;
import com.testehan.finana.repository.SecFilingRepository;
import com.testehan.finana.repository.SecFilingUrlsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SecFilingServiceTest {

    private SecFilingService secFilingService;

    @Mock private SecFilingUrlsRepository secFilingUrlsRepository;
    @Mock private SecFilingRepository secFilingRepository;
    @Mock private FMPService fmpService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        secFilingService = new SecFilingService(secFilingUrlsRepository, secFilingRepository, fmpService);
    }

    @Test
    void fetchAndSaveSecFilings_recentData_skipsFetch() {
        String symbol = "AAPL";
        SecFilingsUrls existing = new SecFilingsUrls(symbol, new ArrayList<>());
        existing.setLastUpdated(LocalDateTime.now());

        when(secFilingUrlsRepository.findById(symbol)).thenReturn(Optional.of(existing));
        when(fmpService.getSecFilings(symbol)).thenReturn(Mono.empty());

        Mono<Void> result = secFilingService.fetchAndSaveSecFilings(symbol);

        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void fetchAndSaveSecFilings_oldData_callsFmpService() {
        String symbol = "AAPL";
        SecFilingsUrls existing = new SecFilingsUrls(symbol, new ArrayList<>());
        existing.setLastUpdated(LocalDateTime.now().minusDays(31));

        when(secFilingUrlsRepository.findById(symbol)).thenReturn(Optional.of(existing));
        when(fmpService.getSecFilings(symbol)).thenReturn(Mono.just(List.of(new SecFilingUrlData())));
        when(secFilingUrlsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Mono<Void> result = secFilingService.fetchAndSaveSecFilings(symbol);

        StepVerifier.create(result).verifyComplete();
        verify(fmpService).getSecFilings(symbol);
        verify(secFilingUrlsRepository).save(any());
    }
}
