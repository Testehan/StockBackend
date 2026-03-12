package com.testehan.finana.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.testehan.finana.model.reporting.ChecklistReport;
import com.testehan.finana.model.reporting.ChecklistReportSummaryDTO;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.model.reporting.ReportType;
import com.testehan.finana.model.user.UserStock;
import com.testehan.finana.model.user.UserStockStatus;
import com.testehan.finana.repository.UserStockRepository;
import com.testehan.finana.service.reporting.ChecklistReportOrchestrator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/stocks/reporting")
public class ReportingController {

    private final ChecklistReportOrchestrator checklistReportOrchestrator;
    private final UserStockRepository userStockRepository;

    public ReportingController(ChecklistReportOrchestrator checklistReportOrchestrator,
                              UserStockRepository userStockRepository) {
        this.checklistReportOrchestrator = checklistReportOrchestrator;
        this.userStockRepository = userStockRepository;
    }

    @GetMapping(value = "/checklist-stream/{ticker}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getChecklistReport(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "false") boolean recreateReport,
            @RequestParam ReportType reportType) {

        String userEmail = extractUserEmail();
        return checklistReportOrchestrator.getChecklistReport(ticker.toUpperCase(), recreateReport, reportType, userEmail);
    }

    private String extractUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String email = jwtAuth.getToken().getClaimAsString("email");
            if (email != null) {
                return email;
            }
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated user or email claim missing from JWT.");
    }

    @PostMapping("/checklist/{symbol}")
    public ResponseEntity<ChecklistReport> saveChecklistReport(@PathVariable String symbol, @RequestBody List<ReportItem> reportItems, @RequestParam ReportType reportType) {
        ChecklistReport savedReport = checklistReportOrchestrator.saveChecklistReport(symbol.toUpperCase(), reportItems, reportType);
        return new ResponseEntity<>(savedReport, HttpStatus.CREATED);
    }

    @GetMapping("/checklist/summary/{userId}")
    public ResponseEntity<Page<ChecklistReportSummaryDTO>> getChecklistReportsSummary(
            @PathVariable String userId,
            @RequestParam(required = false) UserStockStatus status,
            Pageable pageable) {
        Page<ChecklistReportSummaryDTO> summaryPage = checklistReportOrchestrator.getChecklistReportsSummary(pageable, status);
        List<UserStock> userStocks = userStockRepository.findByUserId(userId);
        Map<String, UserStockStatus> userStockStatusMap = userStocks.stream()
                .collect(Collectors.toMap(UserStock::getStockId, UserStock::getStatus));

        summaryPage.forEach(dto -> {
            UserStockStatus stockStatus = userStockStatusMap.getOrDefault(dto.getTicker(), UserStockStatus.NEW);
            dto.setStatus(stockStatus);
        });

        return new ResponseEntity<>(summaryPage, HttpStatus.OK);
    }
}
