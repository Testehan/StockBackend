package com.testehan.finana.service.reporting;

import com.testehan.finana.model.reporting.GeneratedReport;
import com.testehan.finana.model.reporting.ReportType;
import com.testehan.finana.repository.GeneratedReportRepository;
import com.testehan.finana.repository.UserReportOverrideRepository;
import com.testehan.finana.repository.UserStockRepository;
import com.testehan.finana.service.CompanyDataService;
import com.testehan.finana.service.FinancialDataOrchestrator;
import com.testehan.finana.service.UserCreditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChecklistReportOrchestratorTest {

    private ChecklistReportOrchestrator orchestrator;

    @Mock private FinancialDataOrchestrator financialDataOrchestrator;
    @Mock private CompanyDataService companyDataService;
    @Mock private ChecklistReportPersistenceService checklistReportPersistenceService;
    @Mock private GeneratedReportRepository generatedReportRepository;
    @Mock private UserReportOverrideRepository userReportOverrideRepository;
    @Mock private UserStockRepository userStockRepository;
    @Mock private Executor checklistExecutor;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ReportGenerator ferolReportGenerator;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private UserCreditService userCreditService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock the executor to run the task immediately in the same thread
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(checklistExecutor).execute(any(Runnable.class));

        when(ferolReportGenerator.getReportType()).thenReturn(ReportType.FEROL);
        when(userCreditService.hasAnyCredit(anyString())).thenReturn(true);
        
        when(userReportOverrideRepository.findByUserIdAndSymbolAndReportType(any(), any(), any()))
                .thenReturn(Optional.empty());

        orchestrator = new ChecklistReportOrchestrator(
                financialDataOrchestrator, companyDataService, checklistReportPersistenceService,
                generatedReportRepository, userReportOverrideRepository, userStockRepository, checklistExecutor,
                eventPublisher, List.of(ferolReportGenerator), mongoTemplate, userCreditService
        );
    }

    @Test
    void getChecklistReport_invalidType_publishesMessageAndCompletes() {
        String ticker = "AAPL";
        SseEmitter emitter = orchestrator.getChecklistReport(ticker, false, ReportType.ONE_HUNDRED_BAGGER, "test@test.com");
        
        assertNotNull(emitter);
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void getChecklistReport_existingReport_loadsAndPublishesCompletion() {
        String ticker = "AAPL";
        GeneratedReport generatedReport = new GeneratedReport();
        generatedReport.setSymbol(ticker);
        generatedReport.setFerolReport(new com.testehan.finana.model.reporting.ChecklistReport());
        
        when(generatedReportRepository.findBySymbol(ticker)).thenReturn(Optional.of(generatedReport));

        orchestrator.getChecklistReport(ticker, false, ReportType.FEROL, "test@test.com");

        verify(generatedReportRepository).findBySymbol(ticker);
        verify(eventPublisher, atLeastOnce()).publishEvent(any());
    }

    @Test
    void getChecklistReport_recreateReport_triggersGeneration() throws InterruptedException {
        String ticker = "AAPL";
        
        when(financialDataOrchestrator.ensureFinancialDataIsPresent(ticker)).thenReturn(reactor.core.publisher.Mono.empty());

        orchestrator.getChecklistReport(ticker, true, ReportType.FEROL, "test@test.com");

        verify(financialDataOrchestrator).ensureFinancialDataIsPresent(ticker);
        verify(ferolReportGenerator).generate(eq(ticker), eq(ReportType.FEROL), any(SseEmitter.class));
    }
}
