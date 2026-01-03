package com.testehan.finana.service;

import com.testehan.deepresearch.model.ResearchTopic;
import com.testehan.finana.model.reporting.EarningsPresentationReportEntity;
import com.testehan.finana.model.reporting.NewsReportEntity;
import com.testehan.finana.model.research.ResearchJobRecord;
import com.testehan.finana.repository.EarningsPresentationReportRepository;
import com.testehan.finana.repository.NewsReportRepository;
import com.testehan.finana.repository.ResearchJobRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeepResearchServiceTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private NewsReportRepository newsReportRepository;

    @Mock
    private EarningsPresentationReportRepository earningsPresentationReportRepository;

    @Mock
    private ResearchJobRecordRepository researchJobRecordRepository;

    private DeepResearchService deepResearchService;

    @BeforeEach
    void setUp() {
        lenient().when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        lenient().when(webClientBuilder.build()).thenReturn(webClient);

        deepResearchService = new DeepResearchService(
                webClientBuilder,
                newsReportRepository,
                earningsPresentationReportRepository,
                researchJobRecordRepository,
                "http://localhost:8080"
        );
    }

    @Test
    void getRunningJobs_ReturnsRunningJobs() {
        ResearchJobRecord record = new ResearchJobRecord();
        record.setStatus("running");

        when(researchJobRecordRepository.findAllByStatus("running"))
                .thenReturn(List.of(record));

        List<ResearchJobRecord> result = deepResearchService.getRunningJobs();

        assertEquals(1, result.size());
        assertEquals("running", result.get(0).getStatus());
    }

    @Test
    void trackJob_SavesJobRecord() {
        deepResearchService.trackJob("job-123", "AAPL", ResearchTopic.NEWS);

        verify(researchJobRecordRepository).save(argThat(record ->
                record.getJobId().equals("job-123") &&
                record.getStockTicker().equals("AAPL") &&
                record.getTopic() == ResearchTopic.NEWS &&
                record.getStatus().equals("running")
        ));
    }

    @Test
    void getNewsReport_Found_ReturnsReport() {
        NewsReportEntity entity = new NewsReportEntity();
        entity.setStockTicker("AAPL");
        entity.setCreatedAt(LocalDateTime.now().minusDays(5));

        when(newsReportRepository.findFirstByStockTickerAndCreatedAtAfterOrderByCreatedAtDesc(eq("AAPL"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(entity));

        Mono<NewsReportEntity> result = deepResearchService.getNewsReport("AAPL");

        assertNotNull(result.block());
        assertEquals("AAPL", result.block().getStockTicker());
    }

    @Test
    void getNewsReport_NotFound_ReturnsEmpty() {
        when(newsReportRepository.findFirstByStockTickerAndCreatedAtAfterOrderByCreatedAtDesc(eq("AAPL"), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        Mono<NewsReportEntity> result = deepResearchService.getNewsReport("AAPL");

        assertTrue(result.block() == null);
    }

    @Test
    void getEarningsPresentationReport_Found_ReturnsReport() {
        EarningsPresentationReportEntity entity = new EarningsPresentationReportEntity();
        entity.setStockTicker("AAPL");

        when(earningsPresentationReportRepository.findFirstByStockTickerOrderByCreatedAtDesc("AAPL"))
                .thenReturn(Optional.of(entity));

        Mono<EarningsPresentationReportEntity> result = deepResearchService.getEarningsPresentationReport("AAPL");

        assertNotNull(result.block());
        assertEquals("AAPL", result.block().getStockTicker());
    }

    @Test
    void getEarningsPresentationReport_NotFound_ReturnsEmpty() {
        when(earningsPresentationReportRepository.findFirstByStockTickerOrderByCreatedAtDesc("AAPL"))
                .thenReturn(Optional.empty());

        Mono<EarningsPresentationReportEntity> result = deepResearchService.getEarningsPresentationReport("AAPL");

        assertTrue(result.block() == null);
    }

    @Test
    void getAllEarningsPresentationReports_Found_ReturnsList() {
        EarningsPresentationReportEntity entity = new EarningsPresentationReportEntity();
        entity.setStockTicker("AAPL");
        entity.setStatus("COMPLETED");
        entity.setReport(null);

        lenient().when(earningsPresentationReportRepository.findByStockTickerAndStatusOrderByCreatedAtDesc(
                eq("AAPL"), eq("COMPLETED")))
                .thenReturn(List.of(entity));

        Mono<List<EarningsPresentationReportEntity>> result = deepResearchService.getAllEarningsPresentationReports("AAPL");

        assertNotNull(result.block());
    }

    @Test
    void getAllEarningsPresentationReports_NotFound_ReturnsEmpty() {
        lenient().when(earningsPresentationReportRepository.findByStockTickerAndStatusOrderByCreatedAtDesc(
                eq("AAPL"), eq("COMPLETED")))
                .thenReturn(List.of());

        Mono<List<EarningsPresentationReportEntity>> result = deepResearchService.getAllEarningsPresentationReports("AAPL");

        assertNotNull(result.block());
        assertTrue(result.block().isEmpty());
    }

    @Test
    void getJobStatus_NotFound_ReturnsEmpty() {
        when(researchJobRecordRepository.findById("unknown")).thenReturn(Optional.empty());

        Mono<com.testehan.deepresearch.model.JobStatusResponse> result = deepResearchService.getJobStatus("unknown");

        assertTrue(result.block() == null);
    }
}