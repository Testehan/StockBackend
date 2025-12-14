package com.testehan.finana.service;

import com.testehan.finana.model.filing.*;
import com.testehan.finana.repository.SecFilingRepository;
import com.testehan.finana.repository.SecFilingUrlsRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
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

    public Mono<Void> fetchAndSaveSecFilings(String symbol) {
        return Mono.fromCallable(() -> secFilingUrlsRepository.findById(symbol))
                .flatMap(existingSecFilingsOptional -> {
                    if (existingSecFilingsOptional.isPresent()) {
                        SecFilingsUrls existingSecFilings = existingSecFilingsOptional.get();
                        if (existingSecFilings.getLastUpdated() != null &&
                                ChronoUnit.DAYS.between(existingSecFilings.getLastUpdated(), LocalDateTime.now()) < 30) {
                            LOGGER.info("SEC filings for symbol {} are recent. Skipping fetch.", symbol);
                            return Mono.just(existingSecFilingsOptional);
                        }
                    }
                    return Mono.just(existingSecFilingsOptional);
                })
                .flatMap(existingSecFilingsOptional ->
                        fmpService.getSecFilings(symbol)
                                .flatMap(secFilingData -> {
                                    if (secFilingData == null) {
                                        return Mono.just(existingSecFilingsOptional);
                                    }

                                    SecFilingsUrls secFilingsUrls = existingSecFilingsOptional.orElse(new SecFilingsUrls(symbol, new ArrayList<>()));
                                    List<String> existingDates = secFilingsUrls.getFilings().stream()
                                            .map(SecFilingUrlData::getFilingDate)
                                            .toList();
                                    for (SecFilingUrlData newFiling : secFilingData) {
                                        if (!existingDates.contains(newFiling.getFilingDate())) {
                                            secFilingsUrls.getFilings().add(newFiling);
                                        }
                                    }
                                    secFilingsUrls.setLastUpdated(LocalDateTime.now());
                                    return Mono.fromCallable(() -> secFilingUrlsRepository.save(secFilingsUrls));
                                })
                                .onErrorResume(e -> {
                                    if (existingSecFilingsOptional.isPresent()) {
                                        LOGGER.warn("API call failed for SEC filings of {}. Keeping existing data from {}.", 
                                                    symbol, existingSecFilingsOptional.get().getLastUpdated());
                                        return Mono.just(existingSecFilingsOptional.get());
                                    }
                                    LOGGER.error("API call failed for SEC filings of {} and no cached data available.", symbol);
                                    return Mono.empty();
                                })
                )
                .then();
    }

    public Mono<Void> getAndSaveSecFilings(String symbol) {
        return Mono.fromCallable(() -> secFilingUrlsRepository.findById(symbol))
                .flatMap(secFilingsOptional -> {
                    if (secFilingsOptional.isEmpty()) {
                        LOGGER.warn("No SEC filings found for symbol: {}", symbol);
                        return Mono.just(false);
                    }

                    SecFilingsUrls secFilings = secFilingsOptional.get();
                    Optional<SecFiling> existingSecFilingOptional = secFilingRepository.findById(symbol);

                    List<Mono<Void>> processingMonos = new ArrayList<>();

                    Optional<SecFilingUrlData> latest10K = secFilings.getFilings().stream()
                            .filter(filing -> "10-K".equals(filing.getFormType()) || "20-F".equals(filing.getFormType()))
                            .max(Comparator.comparing(SecFilingUrlData::getFilingDate));

                    latest10K.ifPresent(filing -> {
                        if (shouldReprocess10K(existingSecFilingOptional, filing)) {
                            processingMonos.add(Mono.fromRunnable(() -> 
                                getAndSaveSecFiling(symbol, filing.getFinalLink(), filing.getFormType(), filing.getFilingDate()))
                            );
                        }
                    });

                    Optional<SecFilingUrlData> latest10Q = secFilings.getFilings().stream()
                            .filter(filing -> "10-Q".equals(filing.getFormType()) || "6-K".equals(filing.getFormType()))
                            .max(Comparator.comparing(SecFilingUrlData::getFilingDate));

                    latest10Q.ifPresent(filing -> {
                        if (shouldReprocess10Q(existingSecFilingOptional, filing)) {
                            processingMonos.add(Mono.fromRunnable(() -> 
                                getAndSaveSecFiling(symbol, filing.getFinalLink(), filing.getFormType(), filing.getFilingDate()))
                            );
                        }
                    });

                    if (processingMonos.isEmpty()) {
                        return Mono.just(true);
                    }
                    return Mono.when(processingMonos).then(Mono.just(true));
                })
                .then();
    }

    private void processLatestFiling(SecFilingsUrls secFilings, Optional<SecFiling> existingSecFilingOptional, 
                                     String symbol, java.util.function.Predicate<SecFilingUrlData> filter,
                                     java.util.function.BiPredicate<Optional<SecFiling>, SecFilingUrlData> shouldReprocess) {
        Optional<SecFilingUrlData> latestFiling = secFilings.getFilings().stream()
                .filter(filter)
                .max(Comparator.comparing(SecFilingUrlData::getFilingDate));

        latestFiling.ifPresent(filing -> {
            if (shouldReprocess.test(existingSecFilingOptional, filing)) {
                getAndSaveSecFiling(symbol, filing.getFinalLink(), filing.getFormType(), filing.getFilingDate());
            }
        });
    }

    private boolean shouldReprocess10K(Optional<SecFiling> existingSecFilingOptional, SecFilingUrlData filing) {
        if (existingSecFilingOptional.isPresent() && Objects.nonNull(existingSecFilingOptional.get().getTenKFilings())) {
            Optional<TenKFilings> existing10K = existingSecFilingOptional.get().getTenKFilings().stream()
                    .filter(tenK -> tenK.getFiledAt().equals(filing.getFilingDate()))
                    .findFirst();
            if (existing10K.isPresent()) {
                TenKFilings tenK = existing10K.get();
                return tenK.getBusinessDescription() == null || tenK.getRiskFactors() == null || tenK.getManagementDiscussion() == null;
            }
        }
        return true;
    }

    private boolean shouldReprocess10Q(Optional<SecFiling> existingSecFilingOptional, SecFilingUrlData filing) {
        if (existingSecFilingOptional.isPresent() && Objects.nonNull(existingSecFilingOptional.get().getTenQFilings())) {
            Optional<TenQFilings> existing10Q = existingSecFilingOptional.get().getTenQFilings().stream()
                    .filter(tenQ -> tenQ.getFiledAt().equals(filing.getFilingDate()))
                    .findFirst();
            if (existing10Q.isPresent()) {
                TenQFilings tenQ = existing10Q.get();
                return tenQ.getRiskFactors() == null || tenQ.getManagementDiscussion() == null;
            }
        }
        return true;
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
            String riskFactors = null;
            String managementDiscussion = null;

            if ("10-K".equals(formType)) {
                businessDescription = extractSection(fullText, "Item\\s+1[:.]\\s+Business", "Item\\s+1A[:.]\\s+Risk", 1000);
                riskFactors = extractSection(fullText, "Item\\s+1A[:.]\\s+Risk", "Item\\s+1B[:.]\\s+Unresolved|Item\\s+2[:.]\\s+Properties", 1000);
                managementDiscussion = extractSection(fullText, "Item\\s+7[:.]\\s+Management", "Item\\s+7A[:.]\\s+Quantitative", 1000);
            } else if ("20-F".equals(formType)) {
                businessDescription = extract20FBusiness(fullText);
                managementDiscussion = extract20FMDA(fullText);
                riskFactors = extract20FRiskFactors(fullText);
            } else if ("10-Q".equals(formType)) {
                riskFactors = extractSection(fullText, "Item\\s+1A[:.]\\s+Risk", "Item\\s+2[:.]\\s+Unregistered", 150);
                managementDiscussion = extractSection(fullText, "Item\\s+2[:.]\\s+Management", "Item\\s+3[:.]\\s+Quantitative", 500);
            }

            saveFiling(symbol, formType, finalLink, filingDate, businessDescription, riskFactors, managementDiscussion);
            LOGGER.info("Successfully saved SEC filing for symbol: {}", symbol);

        } catch (Exception e) {
            LOGGER.error("Error processing SEC filing for symbol: " + symbol, e);
        }
    }

    private String extract20FBusiness(String fullText) {
        return extractSection(fullText,
                "(?i)ITEM[\\s\\u00A0]+4[\\s\\u00A0]*[.:]?[\\s\\u00A0]*INFORMATION[\\s\\u00A0]+ON[\\s\\u00A0]+(?:THE[\\s\\u00A0]+)?COMPANY",
                "(?i)ITEM[\\s\\u00A0]+(?:4A|5)[\\s\\u00A0]*[.:]",
                2000);
    }

    private String extract20FMDA(String fullText) {
        return extractSection(fullText,
                "(?i)ITEM[\\s\\u00A0]+5[\\s\\u00A0]*[.:]?[\\s\\u00A0]*OPERATING[\\s\\u00A0]+AND[\\s\\u00A0]+FINANCIAL(?![^\\n]*\\.{3,})(?![^\\n]*[\"\"])",
                "(?i)ITEM[\\s\\u00A0]+6[\\s\\u00A0]*[.:]",
                2000);
    }

    private String extract20FRiskFactors(String fullText) {
        // Try multiple patterns for risk factors
        String riskFactors = extractSection(fullText,
                "(?i)(?:ITEM[\\s\\u00A0]+3[\\s\\u00A0]*[.:]?[\\s\\u00A0]*)?D[\\s\\u00A0]*[.:]?[\\s\\u00A0]*RISK[\\s\\u00A0]+FACTORS(?![^\\n]*\\.{3,})",
                "(?i)(?:ITEM[\\s\\u00A0]+4[\\s\\u00A0]*[.:]|E[\\s\\u00A0]*[.:])",
                2000);

        if (riskFactors == null || riskFactors.trim().length() < 100) {
            riskFactors = extractRiskFactorsFromItem3(fullText);
        }

        if (riskFactors == null || riskFactors.trim().length() < 100) {
            riskFactors = extractSection(fullText,
                    "(?i)(?:^|\\n)[\\s]*RISK[\\s\\u00A0]+FACTORS[\\s]*(?:$|\\n)(?![^\\n]*\\.{3,})",
                    "(?i)ITEM[\\s\\u00A0]+(?:4|5)[\\s\\u00A0]*[.:]",
                    2000);
        }

        return riskFactors;
    }

    private String extractRiskFactorsFromItem3(String fullText) {
        String rawItem3 = extractSection(fullText,
                "(?i)ITEM[\\s\\u00A0]+3[\\s\\u00A0]*[.:]?[\\s\\u00A0]*KEY[\\s\\u00A0]+INFORMATION(?![^\\n]*\\.{3,})",
                "(?i)ITEM[\\s\\u00A0]+4[\\s\\u00A0]*[.:]",
                2000);

        if (rawItem3 != null && rawItem3.length() > 100) {
            String upperRaw = rawItem3.toUpperCase();
            int riskIndex = findRiskFactorsIndex(upperRaw);
            if (riskIndex > 100) {
                return rawItem3.substring(riskIndex);
            }
        }
        return rawItem3;
    }

    private int findRiskFactorsIndex(String upperRaw) {
        int riskIndex = upperRaw.indexOf("D.RISK FACTORS");
        if (riskIndex == -1) riskIndex = upperRaw.indexOf("D. RISK FACTORS");

        if (riskIndex == -1) {
            int idx = upperRaw.indexOf("RISK FACTORS", 200);
            if (idx > 200 && idx > 0) {
                String before = upperRaw.substring(Math.max(0, idx - 20), idx);
                if (before.contains("\n") || before.matches(".*[A-Z][\\s.]+$")) {
                    riskIndex = idx;
                }
            }
        }
        return riskIndex;
    }

    private void saveFiling(String symbol, String formType, String finalLink, String filingDate,
                           String businessDescription, String riskFactors, String managementDiscussion) {
        SecFiling secFiling = secFilingRepository.findById(symbol).orElse(new SecFiling());
        secFiling.setSymbol(symbol);

        if ("10-K".equals(formType) || "20-F".equals(formType)) {
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
        } else if ("10-Q".equals(formType) || "6-K".equals(formType)) {
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
    }

    private String extractSection(String fullText, String startRegex, String endRegex, int minimumNrOfCharsExpectedInSection) {
        Pattern startPattern = Pattern.compile(startRegex, Pattern.CASE_INSENSITIVE);
        Pattern endPattern = Pattern.compile(endRegex, Pattern.CASE_INSENSITIVE);

        Matcher startMatcher = startPattern.matcher(fullText);

        while (startMatcher.find()) {
            int startIndex = startMatcher.start();
            Matcher endMatcher = endPattern.matcher(fullText);

            if (endMatcher.find(startMatcher.end())) {
                int endIndex = endMatcher.start();
                String candidate = fullText.substring(startIndex, endIndex);

                if (candidate.length() > minimumNrOfCharsExpectedInSection) {
                    return candidate.trim();
                }
            }
        }
        return null;
    }

    public void deleteSecFilings(String upperCaseSymbol) {
        secFilingRepository.deleteBySymbol(upperCaseSymbol);
        secFilingUrlsRepository.deleteBySymbol(upperCaseSymbol);
    }

    public boolean hasTenKFilings(String symbol) {
        Optional<SecFiling> secFilingOptional = secFilingRepository.findBySymbol(symbol);
        if (secFilingOptional.isPresent()) {
            SecFiling secFiling = secFilingOptional.get();
            return secFiling.getTenKFilings() != null && !secFiling.getTenKFilings().isEmpty();
        }
        return false;
    }

    public boolean hasTenQFilings(String symbol) {
        Optional<SecFiling> secFilingOptional = secFilingRepository.findBySymbol(symbol);
        if (secFilingOptional.isPresent()) {
            SecFiling secFiling = secFilingOptional.get();
            return secFiling.getTenQFilings() != null && !secFiling.getTenQFilings().isEmpty();
        }
        return false;
    }
}