package com.testehan.finana.util;

import com.testehan.finana.model.CompanyOverview;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DateUtils {
    @NotNull
    public String getDateQuarter(CompanyOverview companyOverview) {
        LocalDate date = LocalDate.parse(companyOverview.getLatestQuarter());

        int month = date.getMonthValue();
        int quarter = (month - 1) / 3 + 1;

        String dateQuarter = date.getYear() + "Q" + quarter;
        return dateQuarter;
    }
}
