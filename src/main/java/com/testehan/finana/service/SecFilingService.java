package com.testehan.finana.service;

import com.testehan.finana.model.SecFiling;
import com.testehan.finana.model.SecFilingUrlData;
import com.testehan.finana.model.SecFilingsUrls;
import com.testehan.finana.model.TenKFilings;
import com.testehan.finana.model.TenQFilings;
import com.testehan.finana.repository.SecFilingRepository;
import com.testehan.finana.repository.SecFilingUrlsRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SecFilingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecFilingService.class);

    private final SecFilingUrlsRepository secFilingUrlsRepository;
    private final SecFilingRepository secFilingRepository;
    private final FMPService fmpService;

    public SecFilingService(SecFilingUrlsRepository secFilingUrlsRepository, SecFilingRepository secFilingRepository, FMPService fmpService) {
        this.secFilingUrlsRepository = secFilingUrlsRepository;
        this.secFilingRepository = secFilingRepository;
        this.fmpService = fmpService;
    }

    public void fetchAndSaveSecFilings(String symbol) {
        Optional<SecFilingsUrls> existingSecFilingsOptional = secFilingUrlsRepository.findById(symbol);
        if (existingSecFilingsOptional.isPresent()) {
            SecFilingsUrls existingSecFilings = existingSecFilingsOptional.get();
            if (existingSecFilings.getLastUpdated() != null &&
                    ChronoUnit.DAYS.between(existingSecFilings.getLastUpdated(), LocalDateTime.now()) < 30) {
                LOGGER.info("SEC filings for symbol {} are recent. Skipping fetch.", symbol);
                return;
            }
        }

        List<SecFilingUrlData> secFilingData = fmpService.getSecFilings(symbol).block();
        if (secFilingData == null) {
            return;
        }

        SecFilingsUrls secFilingsUrls = existingSecFilingsOptional.orElse(new SecFilingsUrls(symbol, new ArrayList<>()));
        List<String> existingDates = secFilingsUrls.getFilings().stream().map(SecFilingUrlData::getFilingDate).toList();
        for (SecFilingUrlData newFiling : secFilingData) {
            if (!existingDates.contains(newFiling.getFilingDate())) {
                secFilingsUrls.getFilings().add(newFiling);
            }
        }
        secFilingsUrls.setLastUpdated(LocalDateTime.now());
        secFilingUrlsRepository.save(secFilingsUrls);
    }

    public void getAndSaveSecFilings(String symbol) {
        Optional<SecFilingsUrls> secFilingsOptional = secFilingUrlsRepository.findById(symbol);
        if (secFilingsOptional.isEmpty()) {
            LOGGER.warn("No SEC filings found for symbol: {}", symbol);
            return;
        }

        SecFilingsUrls secFilings = secFilingsOptional.get();
        Optional<SecFiling> existingSecFilingOptional = secFilingRepository.findById(symbol);

        // Get the latest 10-K filing
        Optional<SecFilingUrlData> latest10K = secFilings.getFilings().stream()
                .filter(filing -> "10-K".equals(filing.getFormType()))
                .max(Comparator.comparing(SecFilingUrlData::getFilingDate));

        latest10K.ifPresent(filing -> {
            boolean reprocess = true;
            if (existingSecFilingOptional.isPresent()) {
                Optional<TenKFilings> existing10K = existingSecFilingOptional.get().getTenKFilings().stream()
                        .filter(tenK -> tenK.getFiledAt().equals(filing.getFilingDate()))
                        .findFirst();
                if (existing10K.isPresent()) {
                    TenKFilings tenK = existing10K.get();
                    if (tenK.getBusinessDescription() != null && tenK.getRiskFactors() != null && tenK.getManagementDiscussion() != null) {
                        reprocess = false;
                    }
                }
            }
            if (reprocess) {
                getAndSaveSecFiling(symbol, filing.getFinalLink(), "10-K", filing.getFilingDate());
            }
        });

        // Get the latest 10-Q filing
        Optional<SecFilingUrlData> latest10Q = secFilings.getFilings().stream()
                .filter(filing -> "10-Q".equals(filing.getFormType()))
                .max(Comparator.comparing(SecFilingUrlData::getFilingDate));

        latest10Q.ifPresent(filing -> {
            boolean reprocess = true;
            if (existingSecFilingOptional.isPresent()) {
                Optional<TenQFilings> existing10Q = existingSecFilingOptional.get().getTenQFilings().stream()
                        .filter(tenQ -> tenQ.getFiledAt().equals(filing.getFilingDate()))
                        .findFirst();
                if (existing10Q.isPresent()) {
                    TenQFilings tenQ = existing10Q.get();
                    if (tenQ.getRiskFactors() != null && tenQ.getManagementDiscussion() != null) {
                        reprocess = false;
                    }
                }
            }
            if (reprocess) {
                getAndSaveSecFiling(symbol, filing.getFinalLink(), "10-Q", filing.getFilingDate());
            }
        });
    }

    private void getAndSaveSecFiling(String symbol, String finalLink, String formType, String filingDate) {
        try {
            Document doc = Jsoup.connect(finalLink)
                    .userAgent("CasaMia.ai admin@casamia.ai")
                    .maxBodySize(0)
                    .timeout(30 * 1000)
                    .get();
            String fullText = doc.text();

            if (fullText.isEmpty()) {
                LOGGER.error("Could not fetch filing text for URL: {}", finalLink);
                return;
            }

            LOGGER.info("Document fetched for symbol '{}'. Total length: {} characters.", symbol, fullText.length());

            String businessDescription = null;
            String riskFactors;
            String managementDiscussion;

            if ("10-K".equals(formType)) {
                businessDescription = extractSection(fullText, "Item\s+1[:.]\s+Business", "Item\s+1A[:.]\s+Risk",1000);
                riskFactors = extractSection(fullText, "Item\s+1A[:.]\s+Risk", "Item\s+1B[:.]\s+Unresolved|Item\s+2[:.]\s+Properties",1000);
                managementDiscussion = extractSection(fullText, "Item\s+7[:.]\s+Management", "Item\s+7A[:.]\s+Quantitative",1000);
            } else { // 10-Q
                riskFactors = extractSection(fullText, "Item\s+1A[:.]\s+Risk", "Item\s+2[:.]\s+Unregistered",150);
                managementDiscussion = extractSection(fullText, "Item\s+2[:.]\s+Management", "Item\s+3[:.]\s+Quantitative",500);
            }


            SecFiling secFiling = secFilingRepository.findById(symbol).orElse(new SecFiling());
            secFiling.setSymbol(symbol);

            if ("10-K".equals(formType)) {
                TenKFilings tenKFilings = new TenKFilings();
                tenKFilings.setBusinessDescription(businessDescription);
                tenKFilings.setRiskFactors(riskFactors);
                tenKFilings.setManagementDiscussion(managementDiscussion);
                tenKFilings.setFiledAt(filingDate);
                tenKFilings.setFilingUrl(finalLink);
                if (secFiling.getTenKFilings() == null) {
                    secFiling.setTenKFilings(new ArrayList<>());
                }
                secFiling.getTenKFilings().add(tenKFilings);
            } else if ("10-Q".equals(formType)) {
                TenQFilings tenQFilings = new TenQFilings();
                tenQFilings.setRiskFactors(riskFactors);
                tenQFilings.setManagementDiscussion(managementDiscussion);
                tenQFilings.setFiledAt(filingDate);
                tenQFilings.setFilingUrl(finalLink);
                if (secFiling.getTenQFilings() == null) {
                    secFiling.setTenQFilings(new ArrayList<>());
                }
                secFiling.getTenQFilings().add(tenQFilings);
            }

            secFilingRepository.save(secFiling);
            LOGGER.info("Successfully saved SEC filing for symbol: {}", symbol);

        } catch (Exception e) {
            LOGGER.error("Error processing SEC filing for symbol: " + symbol, e);
        }
    }

    private String extractSection(String fullText, String startRegex, String endRegex, int minimumNrOfCharsExpectedInSection) {
        // Compile patterns case-insensitive
        Pattern startPattern = Pattern.compile(startRegex, Pattern.CASE_INSENSITIVE);
        Pattern endPattern = Pattern.compile(endRegex, Pattern.CASE_INSENSITIVE);

        Matcher startMatcher = startPattern.matcher(fullText);

        while (startMatcher.find()) {
            int startIndex = startMatcher.start();

            // Search for the end pattern *after* the start found
            Matcher endMatcher = endPattern.matcher(fullText);

            // We only look for the end marker AFTER the start marker
            if (endMatcher.find(startMatcher.end())) {
                int endIndex = endMatcher.start();

                // Extract the content
                String candidate = fullText.substring(startIndex, endIndex);

                // --- HEURISTIC FOR TABLE OF CONTENTS ---
                // If the text between "Item 1" and "Item 1A" is very short (e.g., < 1000 chars),
                // it is likely a Table of Contents entry, not the actual section.
                // We skip it and look for the next match.
                if (candidate.length() > minimumNrOfCharsExpectedInSection) {
                    return candidate.trim();
                }
            }
        }
        return null; // Not found
    }

    public void deleteSecFilings(String upperCaseSymbol) {
        secFilingRepository.deleteBySymbol(upperCaseSymbol);
        secFilingUrlsRepository.deleteBySymbol(upperCaseSymbol);
    }
}
