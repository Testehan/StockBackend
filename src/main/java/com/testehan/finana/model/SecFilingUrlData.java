
package com.testehan.finana.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecFilingUrlData {
    private String cik;
    private String filingDate;
    private String acceptedDate;
    private String formType;
    private String link;
    @Field("final_link")
    private String finalLink;
}
