package com.testehan.finana.service;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.repository.CompanyOverviewRepository;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyDataServiceTest {

    @Mock
    private FMPService fmpService;
    @Mock
    private CompanyOverviewRepository companyOverviewRepository;
    @Mock
    private DateUtils dateUtils;

    @InjectMocks
    private CompanyDataService companyDataService;

    private CompanyOverview companyOverview;
    private final String SYMBOL = "AAPL";

    @BeforeEach
    void setUp() {
        companyOverview = new CompanyOverview();
        companyOverview.setSymbol(SYMBOL);
        companyOverview.setLastUpdated(LocalDateTime.now());
    }

    @Test
    void getCompanyOverview_whenRecentDataExists_returnsCachedData() {
        when(companyOverviewRepository.findBySymbol(SYMBOL)).thenReturn(Optional.of(companyOverview));
        when(dateUtils.isRecent(any(), eq(DateUtils.CACHE_ONE_WEEK))).thenReturn(true);

        StepVerifier.create(companyDataService.getCompanyOverview(SYMBOL))
                .expectNext(List.of(companyOverview))
                .verifyComplete();

        verify(companyOverviewRepository).findBySymbol(SYMBOL);
        verify(fmpService, never()).getCompanyOverview(anyString(), any());
    }

    @Test
    void getCompanyOverview_whenDataIsOld_refreshesFromApi() {
        when(companyOverviewRepository.findBySymbol(SYMBOL)).thenReturn(Optional.of(companyOverview));
        when(dateUtils.isRecent(any(), eq(DateUtils.CACHE_ONE_WEEK))).thenReturn(false);
        when(fmpService.getCompanyOverview(eq(SYMBOL), any())).thenReturn(Mono.just(companyOverview));
        when(companyOverviewRepository.save(any())).thenReturn(companyOverview);

        StepVerifier.create(companyDataService.getCompanyOverview(SYMBOL))
                .expectNext(List.of(companyOverview))
                .verifyComplete();

        verify(fmpService).getCompanyOverview(eq(SYMBOL), any());
        verify(companyOverviewRepository).save(any());
    }

    @Test
    void getCompanyOverview_whenNoDataExists_fetchesFromApi() {
        when(companyOverviewRepository.findBySymbol(SYMBOL)).thenReturn(Optional.empty());
        when(fmpService.getCompanyOverview(eq(SYMBOL), any())).thenReturn(Mono.just(companyOverview));
        when(companyOverviewRepository.save(any())).thenReturn(companyOverview);

        StepVerifier.create(companyDataService.getCompanyOverview(SYMBOL))
                .expectNext(List.of(companyOverview))
                .verifyComplete();

        verify(fmpService).getCompanyOverview(eq(SYMBOL), any());
        verify(companyOverviewRepository).save(any());
    }

    @Test
    void getCompanyOverview_whenApiFailsAndCachedDataExists_returnsCachedData() {
        when(companyOverviewRepository.findBySymbol(SYMBOL)).thenReturn(Optional.of(companyOverview));
        when(dateUtils.isRecent(any(), eq(DateUtils.CACHE_ONE_WEEK))).thenReturn(false);
        when(fmpService.getCompanyOverview(eq(SYMBOL), any())).thenReturn(Mono.error(new RuntimeException("API error")));

        StepVerifier.create(companyDataService.getCompanyOverview(SYMBOL))
                .expectNext(List.of(companyOverview))
                .verifyComplete();
    }

    @Test
    void getCompanyOverview_whenApiFailsAndNoCachedData_returnsError() {
        when(companyOverviewRepository.findBySymbol(SYMBOL)).thenReturn(Optional.empty());
        when(fmpService.getCompanyOverview(eq(SYMBOL), any())).thenReturn(Mono.error(new RuntimeException("API error")));

        StepVerifier.create(companyDataService.getCompanyOverview(SYMBOL))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void hasCompanyOverview_returnsTrueIfPresent() {
        when(companyOverviewRepository.findBySymbol(SYMBOL)).thenReturn(Optional.of(companyOverview));
        assert(companyDataService.hasCompanyOverview(SYMBOL));
    }
}
