package com.testehan.finana.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "company_overviews")
public class CompanyOverview {
    @Id
    private String id;

    @JsonProperty("Symbol")
    private String symbol;

    @JsonProperty("AssetType")
    private String assetType;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Description")
    private String description;

    @JsonProperty("CIK")
    private String cik;

    @JsonProperty("Exchange")
    private String exchange;

    @JsonProperty("Currency")
    private String currency;

    @JsonProperty("Country")
    private String country;

    @JsonProperty("Sector")
    private String sector;

    @JsonProperty("Industry")
    private String industry;

    @JsonProperty("Address")
    private String address;

    @JsonProperty("FiscalYearEnd")
    private String fiscalYearEnd;

    @JsonProperty("LatestQuarter")
    private String latestQuarter;

    @JsonProperty("MarketCapitalization")
    private String marketCapitalization;

    @JsonProperty("EBITDA")
    private String ebitda;

    @JsonProperty("PERatio")
    private String peRatio;

    @JsonProperty("PEGRatio")
    private String pegRatio;

    @JsonProperty("BookValue")
    private String bookValue;

    @JsonProperty("DividendPerShare")
    private String dividendPerShare;

    @JsonProperty("DividendYield")
    private String dividendYield;

    @JsonProperty("EPS")
    private String eps;

    @JsonProperty("RevenuePerShareTTM")
    private String revenuePerShareTTM;

    @JsonProperty("ProfitMargin")
    private String profitMargin;

    @JsonProperty("OperatingMarginTTM")
    private String operatingMarginTTM;

    @JsonProperty("ReturnOnAssetsTTM")
    private String returnOnAssetsTTM;

    @JsonProperty("ReturnOnEquityTTM")
    private String returnOnEquityTTM;

    @JsonProperty("RevenueTTM")
    private String revenueTTM;

    @JsonProperty("GrossProfitTTM")
    private String grossProfitTTM;

    @JsonProperty("DilutedEPSTTM")
    private String dilutedEPSTTM;

    @JsonProperty("QuarterlyEarningsGrowthYOY")
    private String quarterlyEarningsGrowthYOY;

    @JsonProperty("QuarterlyRevenueGrowthYOY")
    private String quarterlyRevenueGrowthYOY;

    @JsonProperty("AnalystTargetPrice")
    private String analystTargetPrice;

    @JsonProperty("TrailingPE")
    private String trailingPE;

    @JsonProperty("ForwardPE")
    private String forwardPE;

    @JsonProperty("PriceToSalesRatioTTM")
    private String priceToSalesRatioTTM;

    @JsonProperty("PriceToBookRatio")
    private String priceToBookRatio;

    @JsonProperty("EVToRevenue")
    private String evToRevenue;

    @JsonProperty("EVToEBITDA")
    private String evToEBITDA;

    @JsonProperty("Beta")
    private String beta;

    @JsonProperty("52WeekHigh")
    private String fiftyTwoWeekHigh;

    @JsonProperty("52WeekLow")
    private String fiftyTwoWeekLow;

    @JsonProperty("50DayMovingAverage")
    private String fiftyDayMovingAverage;

    @JsonProperty("200DayMovingAverage")
    private String twoHundredDayMovingAverage;

    @JsonProperty("SharesOutstanding")
    private String sharesOutstanding;

    @JsonProperty("DividendDate")
    private String dividendDate;

    @JsonProperty("ExDividendDate")
    private String exDividendDate;

    @JsonProperty("OfficialSite")
    private String officialSite;

    @JsonProperty("AnalystRatingStrongBuy")
    private String analystRatingStrongBuy;

    @JsonProperty("AnalystRatingBuy")
    private String analystRatingBuy;

    @JsonProperty("AnalystRatingHold")
    private String analystRatingHold;

    @JsonProperty("AnalystRatingSell")
    private String analystRatingSell;

    @JsonProperty("AnalystRatingStrongSell")
    private String analystRatingStrongSell;

    @JsonProperty("SharesFloat")
    private String sharesFloat;

    @JsonProperty("PercentInsiders")
    private String percentInsiders;

    @JsonProperty("PercentInstitutions")
    private String percentInstitutions;

    private LocalDateTime lastUpdated;
}
