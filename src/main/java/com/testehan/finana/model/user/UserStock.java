package com.testehan.finana.model.user;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "user_stocks")
@CompoundIndex(name = "uniq_user_stock", def = "{'userId': 1, 'stockId': 1}", unique = true)
public class UserStock {
    @Id
    private String id;
    private String userId;
    private String stockId;
    private UserStockStatus status;
    private LocalDateTime followUpAt;
    private List<String> tags;
    private String notes;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
