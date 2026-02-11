package com.testehan.finana.service;

import com.testehan.finana.model.EarningsEstimate;
import com.testehan.finana.model.EarningsHistory;
import com.testehan.finana.model.filing.CompanyEarningsTranscripts;
import com.testehan.finana.model.filing.QuarterlyEarningsTranscript;
import com.testehan.finana.repository.CompanyEarningsTranscriptsRepository;
import com.testehan.finana.repository.EarningsEstimatesRepository;
import com.testehan.finana.repository.EarningsHistoryRepository;
import com.testehan.finana.util.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EarningsServiceTest {

    @Mock
    private AlphaVantageService alphaVantageService;
    @Mock
    private FMPService fmpService;
    @Mock
    private EarningsHistoryRepository earningsHistoryRepository;
    @Mock
    private CompanyEarningsTranscriptsRepository companyEarningsTranscriptsRepository;
    @Mock
    private EarningsEstimatesRepository earningsEstimatesRepository;
    @Mock
    private DateUtils dateUtils;

    @InjectMocks
    private EarningsService earningsService;

    private final String SYMBOL = "AAPL";
    private final String QUARTER = "2023Q1";

    @BeforeEach
    void setUp() {
    }

    @Test
    void getEarningsCallTranscript_whenInDbAndRecent_returnsFromDb() {
        QuarterlyEarningsTranscript qt = new QuarterlyEarningsTranscript();
        qt.setQuarter(QUARTER);
        qt.setLastUpdated(LocalDateTime.now());
        
        CompanyEarningsTranscripts cet = new CompanyEarningsTranscripts();
        cet.setSymbol(SYMBOL);
        cet.setTranscripts(List.of(qt));

        when(companyEarningsTranscriptsRepository.findById(SYMBOL)).thenReturn(Optional.of(cet));
        when(dateUtils.isRecent(any(), eq(DateUtils.CACHE_THREE_MONTHS))).thenReturn(true);

        StepVerifier.create(earningsService.getEarningsCallTranscript(SYMBOL, QUARTER))
                .expectNext(qt)
                .verifyComplete();

        verify(alphaVantageService, never()).fetchEarningsCallTranscriptFromApiAndSave(any(), any());
    }

    @Test
    void getEarningsCallTranscript_whenNotInDb_fetchesFromApi() {
        QuarterlyEarningsTranscript qt = new QuarterlyEarningsTranscript();
        qt.setQuarter(QUARTER);
        
        CompanyEarningsTranscripts cet = new CompanyEarningsTranscripts();
        cet.setSymbol(SYMBOL);
        cet.setTranscripts(List.of(qt));

        when(companyEarningsTranscriptsRepository.findById(SYMBOL)).thenReturn(Optional.empty());
        when(alphaVantageService.fetchEarningsCallTranscriptFromApiAndSave(SYMBOL, QUARTER)).thenReturn(Mono.just(cet));

        StepVerifier.create(earningsService.getEarningsCallTranscript(SYMBOL, QUARTER))
                .expectNext(qt)
                .verifyComplete();
    }

    @Test
    void getEarningsHistory_whenInDbAndRecent_returnsFromDb() {
        EarningsHistory eh = new EarningsHistory();
        eh.setSymbol(SYMBOL);
        eh.setLastUpdated(LocalDateTime.now());

        when(earningsHistoryRepository.findBySymbol(SYMBOL)).thenReturn(Optional.of(eh));
        when(dateUtils.isRecent(any(), eq(DateUtils.CACHE_ONE_WEEK))).thenReturn(true);

        StepVerifier.create(earningsService.getEarningsHistory(SYMBOL))
                .expectNext(eh)
                .verifyComplete();

        verify(fmpService, never()).fetchEarningsHistory(any());
    }

    @Test
    void getEarningsHistory_whenOld_refreshesFromApi() {
        EarningsHistory eh = new EarningsHistory();
        eh.setSymbol(SYMBOL);
        eh.setLastUpdated(LocalDateTime.now().minusWeeks(2));

        when(earningsHistoryRepository.findBySymbol(SYMBOL)).thenReturn(Optional.of(eh));
        when(dateUtils.isRecent(any(), eq(DateUtils.CACHE_ONE_WEEK))).thenReturn(false);
        when(fmpService.fetchEarningsHistory(SYMBOL)).thenReturn(Mono.just(eh));
        when(earningsHistoryRepository.save(any())).thenReturn(eh);

        StepVerifier.create(earningsService.getEarningsHistory(SYMBOL))
                .expectNext(eh)
                .verifyComplete();
    }

    @Test
    void getEarningsEstimates_whenInDbAndRecent_returnsFromDb() {
        EarningsEstimate ee = new EarningsEstimate();
        ee.setSymbol(SYMBOL);
        ee.setLastUpdated(LocalDateTime.now());

        when(earningsEstimatesRepository.findBySymbol(SYMBOL)).thenReturn(Optional.of(ee));
        when(dateUtils.isRecent(any(), eq(DateUtils.CACHE_ONE_WEEK))).thenReturn(true);

        StepVerifier.create(earningsService.getEarningsEstimates(SYMBOL))
                .expectNext(ee)
                .verifyComplete();
    }

    @Test
    void getAvailableEarningsQuarters_returnsSortedQuarters() {
        QuarterlyEarningsTranscript qt1 = new QuarterlyEarningsTranscript();
        qt1.setQuarter("2023Q1");
        QuarterlyEarningsTranscript qt2 = new QuarterlyEarningsTranscript();
        qt2.setQuarter("2022Q4");
        
        CompanyEarningsTranscripts cet = new CompanyEarningsTranscripts();
        cet.setTranscripts(List.of(qt1, qt2));

        when(companyEarningsTranscriptsRepository.findById(SYMBOL)).thenReturn(Optional.of(cet));
        when(dateUtils.getCurrentQuarter()).thenReturn("2023Q1");

        StepVerifier.create(earningsService.getAvailableEarningsQuarters(SYMBOL))
                .expectNextMatches(list -> list.size() == 2 && list.get(0).equals("2022Q4") && list.get(1).equals("2023Q1"))
                .verifyComplete();
    }
}
