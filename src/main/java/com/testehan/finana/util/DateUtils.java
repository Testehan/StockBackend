package com.testehan.finana.util;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DateUtils {
    @NotNull
    public String getDateQuarter(String dateString) {

        LocalDate date = LocalDate.parse(dateString);

        int month = date.getMonthValue();
        int quarter = (month - 1) / 3 + 1;

        String dateQuarter = date.getYear() + "Q" + quarter;
        return dateQuarter;
    }
}
