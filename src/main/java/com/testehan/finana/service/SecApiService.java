package com.testehan.finana.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.testehan.finana.model.SecFiling;
import com.testehan.finana.model.TenKFilings;
import com.testehan.finana.model.TenQFilings;
import com.testehan.finana.repository.SecFilingRepository;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class SecApiService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecApiService.class);
    private final WebClient webClient;

    @Value("${sec.api.key}")
    private String apiKey;

    private final SecFilingRepository secFilingRepository;

    private static final Map<String, SectionConfig<TenKFilings>> TEN_K_SECTIONS = new java.util.HashMap<>();
    private static final Map<String, SectionConfig<TenQFilings>> TEN_Q_SECTIONS = new java.util.HashMap<>();

    static {
        TEN_K_SECTIONS.put("risk_factors", new SectionConfig<>("1A", TenKFilings::getRiskFactors, TenKFilings::setRiskFactors, 6));
        TEN_K_SECTIONS.put("management_discussion", new SectionConfig<>("7", TenKFilings::getManagementDiscussion, TenKFilings::setManagementDiscussion, 6));
        TEN_K_SECTIONS.put("business_description", new SectionConfig<>("1", TenKFilings::getBusinessDescription, TenKFilings::setBusinessDescription, 12));

        TEN_Q_SECTIONS.put("risk_factors", new SectionConfig<>("part2item1a", TenQFilings::getRiskFactors, TenQFilings::setRiskFactors, 0, 60));
        TEN_Q_SECTIONS.put("management_discussion", new SectionConfig<>("part1item2", TenQFilings::getManagementDiscussion, TenQFilings::setManagementDiscussion, 0, 60));
    }

    @Data
    private static class SectionConfig<T> {
        private final String item;
        private final java.util.function.Function<T, String> getSection;
        private final java.util.function.BiConsumer<T, String> setSection;
        private final int cacheMonths;
        private final int cacheDays;

        public SectionConfig(String item, java.util.function.Function<T, String> getSection, java.util.function.BiConsumer<T, String> setSection, int cacheMonths, int cacheDays) {
            this.item = item;
            this.getSection = getSection;
            this.setSection = setSection;
            this.cacheMonths = cacheMonths;
            this.cacheDays = cacheDays;
        }

        public SectionConfig(String item, java.util.function.Function<T, String> getSection, java.util.function.BiConsumer<T, String> setSection, int cacheMonths) {
            this(item, getSection, setSection, cacheMonths, 0);
        }
    }

    public SecApiService(WebClient.Builder webClientBuilder, SecFilingRepository secFilingRepository) {
        this.webClient = webClientBuilder.baseUrl("https://api.sec-api.io").build();
        this.secFilingRepository = secFilingRepository;
    }

    public Mono<String> getSectionFrom10K(String ticker, String section) {
        SectionConfig<TenKFilings> config = TEN_K_SECTIONS.get(section);
        if (config == null) {
            return Mono.error(new IllegalArgumentException("Invalid section for 10-K: " + section));
        }

        SectionRequest<TenKFilings> request = SectionRequest.<TenKFilings>builder()
                .ticker(ticker)
                .formType("10-K")
                .item(config.getItem())
                .filingClass(TenKFilings.class)
                .getFilings(SecFiling::getTenKFilings)
                .setFilings(SecFiling::setTenKFilings)
                .getFiledAt(TenKFilings::getFiledAt)
                .getFilingUrl(TenKFilings::getFilingUrl)
                .getSectionFromFiling(config.getGetSection())
                .setSectionInFiling(config.getSetSection())
                .setFiledAtInFiling(TenKFilings::setFiledAt)
                .setFilingUrlInFiling(TenKFilings::setFilingUrl)
                .newFilingInstance(TenKFilings::new)
                .cacheMonths(config.getCacheMonths())
                .cacheDays(config.getCacheDays())
                .build();

        return getSection(request);
    }

    public Mono<String> getSectionFrom10Q(String ticker, String section) {
        SectionConfig<TenQFilings> config = TEN_Q_SECTIONS.get(section);
        if (config == null) {
            return Mono.error(new IllegalArgumentException("Invalid section for 10-Q: " + section));
        }

        SectionRequest<TenQFilings> request = SectionRequest.<TenQFilings>builder()
                .ticker(ticker)
                .formType("10-Q")
                .item(config.getItem())
                .filingClass(TenQFilings.class)
                .getFilings(SecFiling::getTenQFilings)
                .setFilings(SecFiling::setTenQFilings)
                .getFiledAt(TenQFilings::getFiledAt)
                .getFilingUrl(TenQFilings::getFilingUrl)
                .getSectionFromFiling(config.getGetSection())
                .setSectionInFiling(config.getSetSection())
                .setFiledAtInFiling(TenQFilings::setFiledAt)
                .setFilingUrlInFiling(TenQFilings::setFilingUrl)
                .newFilingInstance(TenQFilings::new)
                .cacheMonths(config.getCacheMonths())
                .cacheDays(config.getCacheDays())
                .build();

        return getSection(request);
    }

    @Data
    @lombok.Builder
    private static class SectionRequest<T> {
        private String ticker;
        private String formType;
        private String item;
        private Class<T> filingClass;
        private java.util.function.Function<SecFiling, List<T>> getFilings;
        private java.util.function.BiConsumer<SecFiling, List<T>> setFilings;
        private java.util.function.Function<T, String> getFiledAt;
        private java.util.function.Function<T, String> getFilingUrl;
        private java.util.function.Function<T, String> getSectionFromFiling;
        private java.util.function.BiConsumer<T, String> setSectionInFiling;
        private java.util.function.BiConsumer<T, String> setFiledAtInFiling;
        private java.util.function.BiConsumer<T, String> setFilingUrlInFiling;
        private java.util.function.Supplier<T> newFilingInstance;
        private int cacheMonths;
        private int cacheDays;
    }

    private <T> Mono<String> getSection(SectionRequest<T> request) {

        SecFiling secFiling = secFilingRepository.findById(request.getTicker()).orElseGet(() -> {
            SecFiling newFiling = new SecFiling();
            newFiling.setSymbol(request.getTicker());
            return newFiling;
        });

        if (request.getGetFilings().apply(secFiling) != null && !request.getGetFilings().apply(secFiling).isEmpty()) {
            T latestInDb = request.getGetFilings().apply(secFiling).stream()
                    .max(Comparator.comparing(f -> OffsetDateTime.parse(request.getGetFiledAt().apply(f))))
                    .orElse(null);

            if (latestInDb != null) {
                boolean isCacheValid = (request.getCacheMonths() > 0 && OffsetDateTime.parse(request.getGetFiledAt().apply(latestInDb)).isAfter(OffsetDateTime.now().minusMonths(request.getCacheMonths()))) ||
                        (request.getCacheDays() > 0 && OffsetDateTime.parse(request.getGetFiledAt().apply(latestInDb)).isAfter(OffsetDateTime.now().minusDays(request.getCacheDays())));
                if (isCacheValid) {
                    if (request.getGetSectionFromFiling().apply(latestInDb) != null) {
                        return Mono.just(request.getGetSectionFromFiling().apply(latestInDb));
                    } else {
                        SecFiling finalSecFiling = secFiling;
                        return extractSection(request.getGetFilingUrl().apply(latestInDb), request.getItem())
                                .flatMap(section -> {
                                    request.getSetSectionInFiling().accept(latestInDb, section);
                                    secFilingRepository.save(finalSecFiling);
                                    return Mono.just(section);
                                });
                    }
                }
            }
        }

        return getLatestFiling(request.getTicker(), request.getFormType())
                .doOnNext(filing -> LOGGER.info("Found latest filing: {}", filing))
                .switchIfEmpty(Mono.defer(() -> {
                    LOGGER.warn("No latest filing found for ticker: {}", request.getTicker());
                    return Mono.empty();
                }))
                .flatMap(latestFilingFromApi -> {
                    String url = latestFilingFromApi.getLinkToFilingDetails() != null ? latestFilingFromApi.getLinkToFilingDetails() : latestFilingFromApi.getFilingUrl();
                    SecFiling secFilingToSave = secFilingRepository.findById(request.getTicker()).orElseGet(() -> {
                        SecFiling newFiling = new SecFiling();
                        newFiling.setSymbol(request.getTicker());
                        return newFiling;
                    });
                    if (request.getGetFilings().apply(secFilingToSave) == null) {
                        request.getSetFilings().accept(secFilingToSave, new java.util.ArrayList<>());
                    }

                    Optional<T> existingFiling = request.getGetFilings().apply(secFilingToSave).stream()
                            .filter(f -> request.getGetFiledAt().apply(f).equals(latestFilingFromApi.getFiledAt()))
                            .findFirst();

                    return extractSection(url, request.getItem())
                            .flatMap(section -> {
                                T filingToSave;
                                if (existingFiling.isPresent()) {
                                    filingToSave = existingFiling.get();
                                } else {
                                    filingToSave = request.getNewFilingInstance().get();
                                    request.getSetFiledAtInFiling().accept(filingToSave, latestFilingFromApi.getFiledAt());
                                    request.getSetFilingUrlInFiling().accept(filingToSave, url);
                                    request.getGetFilings().apply(secFilingToSave).add(filingToSave);
                                }
                                request.getSetSectionInFiling().accept(filingToSave, section);
                                LOGGER.info("Saving SecFiling for ticker: {}", secFilingToSave.getSymbol());
                                secFilingRepository.save(secFilingToSave);
                                return Mono.just(section);
                            });
                });
    }

    private Mono<Filing> getLatestFiling(String ticker, String formType) {
        FilingQuery filingQuery = new FilingQuery();
        Query query = new Query();
        QueryString queryString = new QueryString();
        queryString.setQuery("ticker:" + ticker.toUpperCase() + " AND formType:\"" + formType + "\"");
        LOGGER.info("Querying sec-api.io with query: {}", queryString.getQuery());
        query.setQueryString(queryString);
        filingQuery.setQuery(query);
        filingQuery.setFrom(0);
        filingQuery.setSize(1);
        Sort sort = new Sort();
        FiledAt filedAt = new FiledAt();
        filedAt.setOrder("desc");
        sort.setFiledAt(filedAt);
        filingQuery.setSort(Collections.singletonList(sort));

        return webClient.post()
                .uri("/")
                .header("Authorization", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(filingQuery), FilingQuery.class)
                .retrieve()
                .bodyToMono(FilingsResponse.class)
                .doOnNext(response -> LOGGER.info("Response from sec-api.io: {}", response))
                .flatMap(filingsResponse -> {
                    if (filingsResponse != null && filingsResponse.getFilings() != null && !filingsResponse.getFilings().isEmpty()) {
                        return Mono.just(filingsResponse.getFilings().get(0));
                    } else {
                        LOGGER.warn("No filings found in the response from sec-api.io");
                        return Mono.empty();
                    }
                });
    }

    private Mono<String> extractSection(String filingUrl, String item) {
        LOGGER.info("Extracting section {} from URL {}", item, filingUrl);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/extractor")
                        .queryParam("url", filingUrl)
                        .queryParam("item", item)
                        .queryParam("type", "text")
                        .queryParam("token", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(section -> LOGGER.info("Extracted section {} content (first 50 chars): {}", item, section.substring(0, Math.min(50, section.length()))))
                .switchIfEmpty(Mono.defer(() -> {
                    LOGGER.warn("No content extracted for section {} from URL {}", item, filingUrl);
                    return Mono.empty();
                }));
    }




    @Data
    private static class FilingQuery {
        private Query query;
        private int from;
        private int size;
        private List<Sort> sort;
    }

    @Data
    private static class Query {
        @JsonProperty("query_string")
        private QueryString queryString;
    }

    @Data
    private static class QueryString {
        private String query;
    }

    @Data
    private static class Sort {
        private FiledAt filedAt;
    }

    @Data
    private static class FiledAt {
        private String order;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FilingsResponse {
        private List<Filing> filings;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Filing {
        private String linkToFilingDetails;
        private String filingUrl;
        private String filedAt;
    }
}

