package com.testehan.finana.util;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class DateUtils {

    public static final int CACHE_TEN_MINUTES = 10;
    public static final int CACHE_HOUR_AND_A_HALF = 100;
    public static final int CACHE_ONE_WEEK = 10080;
    public static final int CACHE_ONE_MONTH = 43200;

    @NotNull
    public String getDateQuarter(String dateString) {

        LocalDate date = LocalDate.parse(dateString);

        int month = date.getMonthValue();
        int quarter = (month - 1) / 3 + 1;

        String dateQuarter = date.getYear() + "Q" + quarter;
        return dateQuarter;
    }

    public LocalDate parseDate(String dateStr, java.time.format.DateTimeFormatter formatter) {
        return LocalDate.parse(dateStr, formatter);
    }

    public boolean isRecent(LocalDateTime lastUpdated, int minutes) {
        if (lastUpdated == null) {
            return false;
        }
        return ChronoUnit.MINUTES.between(lastUpdated, LocalDateTime.now()) < minutes;
    }
}
