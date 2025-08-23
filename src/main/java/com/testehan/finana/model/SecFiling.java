package com.testehan.finana.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "sec_filings")
public class SecFiling {
    @Id
    private String symbol;
    private List<TenKFilings> tenKFilings;
    private List<TenQFilings> tenQFilings;
}
