package com.testehan.finana.service.reporting;

import com.testehan.finana.model.*;
import com.testehan.finana.model.reporting.*;
import com.testehan.finana.model.user.UserStock;
import com.testehan.finana.model.user.UserStockStatus;
import com.testehan.finana.repository.GeneratedReportRepository;
import com.testehan.finana.repository.UserStockRepository;
import com.testehan.finana.service.CompanyDataService;
import com.testehan.finana.service.FinancialDataOrchestrator;
import com.testehan.finana.service.reporting.events.CompletionEvent;
import com.testehan.finana.service.reporting.events.ErrorEvent;
import com.testehan.finana.service.reporting.events.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChecklistReportOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChecklistReportOrchestrator.class);

    private final FinancialDataOrchestrator financialDataOrchestrator;
    private final CompanyDataService companyDataService;
    private final ChecklistReportPersistenceService checklistReportPersistenceService;
    private final GeneratedReportRepository generatedReportRepository;
    private final UserStockRepository userStockRepository;
    private final Executor checklistExecutor;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<ReportType, ReportGenerator> reportGenerators;
    private final MongoTemplate mongoTemplate;

    public ChecklistReportOrchestrator(FinancialDataOrchestrator financialDataOrchestrator,
                                       CompanyDataService companyDataService,
                                       ChecklistReportPersistenceService checklistReportPersistenceService,
                                       GeneratedReportRepository generatedReportRepository,
                                       UserStockRepository userStockRepository,
                                       @Qualifier("checklistExecutor") Executor checklistExecutor,
                                       ApplicationEventPublisher eventPublisher,
                                       List<ReportGenerator> reportGenerators,
                                       MongoTemplate mongoTemplate) {
        this.financialDataOrchestrator = financialDataOrchestrator;
        this.companyDataService = companyDataService;
        this.checklistReportPersistenceService = checklistReportPersistenceService;
        this.generatedReportRepository = generatedReportRepository;
        this.userStockRepository = userStockRepository;
        this.checklistExecutor = checklistExecutor;
        this.eventPublisher = eventPublisher;
        this.reportGenerators = reportGenerators.stream()
                .collect(Collectors.toMap(ReportGenerator::getReportType, Function.identity()));
        this.mongoTemplate = mongoTemplate;
    }

    public SseEmitter getChecklistReport(String ticker, boolean recreateReport, ReportType reportType) {
        SseEmitter sseEmitter = new SseEmitter(3600000L); // Timeout set to 1 hour

        ReportGenerator generator = reportGenerators.get(reportType);
        if (generator == null) {
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Invalid report type."));
            sseEmitter.complete();
            return sseEmitter;
        }

        getOrGenerateChecklistReport(ticker, recreateReport, reportType, sseEmitter);
        return sseEmitter;
    }

    private void getOrGenerateChecklistReport(String ticker, boolean recreateReport, ReportType reportType, SseEmitter sseEmitter) {
        checklistExecutor.execute(() -> {
            try {
                if (!recreateReport) {
                    eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Attempting to load report from database..."));
                    Optional<GeneratedReport> existingGeneratedReport = generatedReportRepository.findBySymbol(ticker);
                    if (existingGeneratedReport.isPresent()) {
                        ChecklistReport checklistReport = getReportFromGeneratedReport(existingGeneratedReport.get(), reportType);
                        if (Objects.nonNull(checklistReport)) {
                            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Report loaded from database."));
                            checklistReport.setGeneratedAt(getReportDate(existingGeneratedReport.get(),reportType));
                            eventPublisher.publishEvent(new CompletionEvent(this, ticker, sseEmitter, checklistReport));
                            LOGGER.info("Checklist report for {} loaded from DB and sent.", ticker);
                            return; // Exit as report is sent
                        }
                    }
                    // If not recreated, and report not found or incomplete,
                    // send message and complete emitter
                    eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Report not found in database or incomplete. You must generate a new report."));
                    sseEmitter.complete();
                } else {
                    eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Initiating Checklist report generation for " + ticker + "..."));
                    generateReport(ticker, reportType, sseEmitter);
                }
            } catch (Exception e) {
                eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, e));
            }
        });
    }

    private void generateReport(String ticker, ReportType reportType, SseEmitter sseEmitter) {
        eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Ensuring financial data is present..."));

        financialDataOrchestrator.ensureFinancialDataIsPresent(ticker)
                .doOnSuccess(v -> {
                    eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Financial data check complete."));
                    ReportGenerator generator = reportGenerators.get(reportType);
                    try {
                        generator.generate(ticker, reportType, sseEmitter);
                    } catch (Exception e) {
                        eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, e));
                    }
                })
                .doOnError(error -> {
                    eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, error));
                })
                .subscribe();
    }

    public ChecklistReport saveChecklistReport(String ticker, List<ReportItem> checklistReportItems, ReportType reportType) {
        LOGGER.info("Saving Checklist report for {}", ticker);
        return checklistReportPersistenceService.buildAndSaveReport(ticker, checklistReportItems, reportType, LocalDateTime.now());
    }

    public Page<ChecklistReportSummaryDTO> getChecklistReportsSummary(Pageable pageable, UserStockStatus status) {
        LOGGER.info("Received Pageable with sort: {}", pageable.getSort());

        Set<String> tickersWithStatus = null;
        if (status != null) {
            List<UserStock> userStocksWithStatus = userStockRepository.findByStatus(status);
            tickersWithStatus = userStocksWithStatus.stream()
                    .map(UserStock::getStockId)
                    .collect(Collectors.toSet());
        }

        boolean isPrimarySortOnCompanyOverview = pageable.getSort().stream()
                .anyMatch(order -> "ticker".equals(order.getProperty())); // Check if ticker is in sort

        if (isPrimarySortOnCompanyOverview) {
            // Case 1: Primary sort on CompanyOverview (e.g., by ticker)
            // Fetch all CompanyOverviews, then enrich with GeneratedReport data

            // Create a Pageable specifically for CompanyOverview, including collation for symbol (ticker)
            Sort companyOverviewSort = Sort.by(pageable.getSort().stream()
                    .map(order -> {
                        String property = order.getProperty();
                        if ("ticker".equals(property)) {
                            return new org.springframework.data.domain.Sort.Order(order.getDirection(), "symbol"); // Map DTO ticker to CompanyOverview symbol
                        }
                        // If other CompanyOverview properties become sortable, they would be mapped here.
                        return order;
                    }).collect(Collectors.toList()));

            Pageable companyOverviewPageable = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), companyOverviewSort);

            Query companyOverviewQuery = new Query().with(companyOverviewPageable);
            if (companyOverviewSort.stream().anyMatch(order -> "symbol".equals(order.getProperty()))) {
                companyOverviewQuery.collation(Collation.of(Locale.ENGLISH).strength(1));
            }

            if (tickersWithStatus != null && !tickersWithStatus.isEmpty()) {
                companyOverviewQuery.addCriteria(Criteria.where("symbol").in(tickersWithStatus));
            }

            List<CompanyOverview> pagedCompanyOverviewsContent = mongoTemplate.find(companyOverviewQuery, CompanyOverview.class, "company_overviews");
            long totalCompanyOverviews = mongoTemplate.count(Query.of(companyOverviewQuery).limit(-1).skip(-1).with(Sort.unsorted()), CompanyOverview.class, "company_overviews");
            Page<CompanyOverview> pagedCompanyOverviews = new PageImpl<>(pagedCompanyOverviewsContent, companyOverviewPageable, totalCompanyOverviews);

            List<String> tickers = pagedCompanyOverviews.getContent().stream()
                    .map(CompanyOverview::getSymbol)
                    .collect(Collectors.toList());

            final Map<String, GeneratedReport> generatedReportMap ;
            if (!tickers.isEmpty()) {
                List<GeneratedReport> generatedReports = generatedReportRepository.findBySymbolIn(tickers);
                generatedReportMap = generatedReports.stream()
                        .collect(Collectors.toMap(GeneratedReport::getSymbol, Function.identity()));
            } else {
                generatedReportMap = new HashMap<>();
            }

            List<ChecklistReportSummaryDTO> summaryList = pagedCompanyOverviews.getContent().stream()
                    .map(company -> {
                        String ticker = company.getSymbol();
                        GeneratedReport generatedReport = generatedReportMap.get(ticker);

                        int totalFerolScore = (generatedReport != null && generatedReport.getTotalFerolScore() != null) ? generatedReport.getTotalFerolScore() : 0;
                        LocalDateTime ferolReportGeneratedAt = (generatedReport != null) ? generatedReport.getFerolReportGeneratedAt() : null;
                        int totalHundredBaggerScore = (generatedReport != null && generatedReport.getTotalOneHundredBaggerScore() != null) ? generatedReport.getTotalOneHundredBaggerScore() : 0;
                        LocalDateTime hundredBaggerReportGeneratedAt = (generatedReport != null) ? generatedReport.getOneHundredBaggerReportGeneratedAt() : null;

                        return new ChecklistReportSummaryDTO(ticker, totalFerolScore, totalHundredBaggerScore, ferolReportGeneratedAt, hundredBaggerReportGeneratedAt);
                    })
                    .collect(Collectors.toList());

            return new PageImpl<>(summaryList, pageable, pagedCompanyOverviews.getTotalElements());

        } else {
            // Case 2: Primary sort on GeneratedReport (by score, date, or no sort)

            Query query = new Query();
            List<Sort.Order> modifiedOrders = new java.util.ArrayList<>();
            boolean has100BaggerDateSort = false;
            boolean hasFerolDateSort = false;

            for (Sort.Order order : pageable.getSort()) {
                if ("total100BaggerScore".equals(order.getProperty())) {
                    modifiedOrders.add(new org.springframework.data.domain.Sort.Order(order.getDirection(), "totalOneHundredBaggerScore"));
                } else if ("generationFerolDate".equals(order.getProperty())) {
                    hasFerolDateSort = true;
                    modifiedOrders.add(new org.springframework.data.domain.Sort.Order(order.getDirection(), "ferolReportGeneratedAt"));
                } else if ("generation100BaggerDate".equals(order.getProperty())) {
                    has100BaggerDateSort = true;
                    modifiedOrders.add(new org.springframework.data.domain.Sort.Order(order.getDirection(), "oneHundredBaggerReportGeneratedAt"));
                } else {
                    modifiedOrders.add(order); // Keep other orders as they are (e.g., totalFerolScore)
                }
            }

            if (!modifiedOrders.isEmpty()) {
                query.with(Sort.by(modifiedOrders));
            } else {
                query.with(pageable.getSort());
            }

            if (tickersWithStatus != null && !tickersWithStatus.isEmpty()) {
                query.addCriteria(Criteria.where("symbol").in(tickersWithStatus));
            }

            query.skip(pageable.getOffset()).limit(pageable.getPageSize());

            if (hasFerolDateSort) {
                query.addCriteria(Criteria.where("ferolReportGeneratedAt").exists(true).ne(null));
            }
            if (has100BaggerDateSort) {
                query.addCriteria(Criteria.where("oneHundredBaggerReportGeneratedAt").exists(true).ne(null));
            }

            List<GeneratedReport> content = mongoTemplate.find(query, GeneratedReport.class, "generated_reports");
            long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1).with(Sort.unsorted()), GeneratedReport.class, "generated_reports");

            Page<GeneratedReport> pagedGeneratedReports = new PageImpl<>(content, pageable, total);

            List<String> tickers = pagedGeneratedReports.getContent().stream()
                    .map(GeneratedReport::getSymbol)
                    .collect(Collectors.toList());

            List<CompanyOverview> companyOverviews = companyDataService.findBySymbolsIn(tickers);
            Map<String, CompanyOverview> companyOverviewMap = companyOverviews.stream()
                    .collect(Collectors.toMap(CompanyOverview::getSymbol, Function.identity()));

            List<ChecklistReportSummaryDTO> summaryList = pagedGeneratedReports.getContent().stream()
                    .map(generatedReport -> {
                        String ticker = generatedReport.getSymbol();
                        CompanyOverview company = companyOverviewMap.get(ticker);

                        int totalFerolScore = generatedReport.getTotalFerolScore() != null ? generatedReport.getTotalFerolScore() : 0;
                        LocalDateTime ferolReportGeneratedAt = generatedReport.getFerolReportGeneratedAt();
                        int totalHundredBaggerScore = generatedReport.getTotalOneHundredBaggerScore() != null ? generatedReport.getTotalOneHundredBaggerScore() : 0;
                        LocalDateTime hundredBaggerReportGeneratedAt = generatedReport.getOneHundredBaggerReportGeneratedAt();

                        return new ChecklistReportSummaryDTO(ticker, totalFerolScore, totalHundredBaggerScore, ferolReportGeneratedAt, hundredBaggerReportGeneratedAt);
                    })
                    .collect(Collectors.toList());

            return new PageImpl<>(summaryList, pageable, pagedGeneratedReports.getTotalElements());
        }
    }

    private ChecklistReport getReportFromGeneratedReport(GeneratedReport generatedReport, ReportType reportType) {
        return switch (reportType) {
            case FEROL -> generatedReport.getFerolReport();
            case ONE_HUNDRED_BAGGER -> generatedReport.getOneHundredBaggerReport();
        };
    }

    private LocalDateTime getReportDate(GeneratedReport generatedReport, ReportType reportType) {
        return switch (reportType) {
            case FEROL -> generatedReport.getFerolReportGeneratedAt();
            case ONE_HUNDRED_BAGGER -> generatedReport.getOneHundredBaggerReportGeneratedAt();
        };
    }
}