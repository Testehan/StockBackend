package com.testehan.finana.model.user;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "user_portfolios")
public class UserPortfolio {
    @Id
    private String id;
    private String userId;
    private List<StockHolding> stocks = new ArrayList<>();
    private List<AssetHolding> otherAssets = new ArrayList<>();

    @Data
    public static class StockHolding {
        private String symbol;
        private BigDecimal shares;
        private BigDecimal purchasePricePerStock;
    }

    @Data
    public static class AssetHolding {
        private String name;
        private BigDecimal value;
    }
}