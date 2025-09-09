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
    @JsonProperty("symbol")
    private String symbol;
    @JsonProperty("price")
    private String price;
    @JsonProperty("marketCap")
    private String marketCap;
    @JsonProperty("beta")
    private String beta;
    @JsonProperty("lastDividend")
    private String lastDividend;
    @JsonProperty("range")
    private String range;
    @JsonProperty("change")
    private String change;
    @JsonProperty("changePercentage")
    private String changePercentage;
    @JsonProperty("volume")
    private String volume;
    @JsonProperty("averageVolume")
    private String averageVolume;
    @JsonProperty("companyName")
    private String companyName;
    @JsonProperty("currency")
    private String currency;
    @JsonProperty("cik")
    private String cik;
    @JsonProperty("isin")
    private String isin;
    @JsonProperty("cusip")
    private String cusip;
    @JsonProperty("exchangeFullName")
    private String exchangeFullName;
    @JsonProperty("exchange")
    private String exchange;
    @JsonProperty("industry")
    private String industry;
    @JsonProperty("website")
    private String website;
    @JsonProperty("description")
    private String description;
    @JsonProperty("ceo")
    private String ceo;
    @JsonProperty("sector")
    private String sector;
    @JsonProperty("country")
    private String country;
    @JsonProperty("fullTimeEmployees")
    private String fullTimeEmployees;
    @JsonProperty("phone")
    private String phone;
    @JsonProperty("address")
    private String address;
    @JsonProperty("city")
    private String city;
    @JsonProperty("state")
    private String state;
    @JsonProperty("zip")
    private String zip;
    @JsonProperty("image")
    private String image;
    @JsonProperty("ipoDate")
    private String ipoDate;
    @JsonProperty("defaultImage")
    private boolean defaultImage;
    @JsonProperty("isEtf")
    private boolean isEtf;
    @JsonProperty("isActivelyTrading")
    private boolean isActivelyTrading;
    @JsonProperty("isAdr")
    private boolean isAdr;
    @JsonProperty("isFund")
    private boolean isFund;

    private LocalDateTime lastUpdated;
}
