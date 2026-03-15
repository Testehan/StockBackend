package com.testehan.finana.repository;

import com.testehan.finana.model.reporting.ReportType;
import com.testehan.finana.model.reporting.UserReportOverride;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserReportOverrideRepository extends MongoRepository<UserReportOverride, String> {
    Optional<UserReportOverride> findByUserIdAndSymbolAndReportType(String userId, String symbol, ReportType reportType);
    List<UserReportOverride> findBySymbolAndReportType(String symbol, ReportType reportType);
    List<UserReportOverride> findByUserIdAndSymbolIn(String userId, List<String> symbols);
}
