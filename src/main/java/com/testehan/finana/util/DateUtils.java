package com.testehan.finana.util;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class DateUtils {

    public static final int CACHE_TEN_MINUTES = 10;
    public static final int CACHE_HOUR_AND_A_HALF = 100;
    public static final int CACHE_ONE_WEEK = 10080;
    public static final int CACHE_ONE_MONTH = 43200;
    public static final int CACHE_THREE_MONTHS = 129600;

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

    public String getCurrentQuarter() {
        LocalDate now = LocalDate.now();
        int quarter = (now.getMonthValue() - 1) / 3 + 1;
        return now.getYear() + "Q" + quarter;
    }

    public List<String> generateQuartersUpToCurrent(String latestQuarter) {
        List<String> quarters = new ArrayList<>();
        
        int currentYear = LocalDate.now().getYear();
        int currentQuarter = (LocalDate.now().getMonthValue() - 1) / 3 + 1;

        String[] parts = latestQuarter.split("Q");
        int latestYear = Integer.parseInt(parts[0]);
        int latestQ = Integer.parseInt(parts[1]);

        for (int year = latestYear; year <= currentYear; year++) {
            int startQ = (year == latestYear) ? latestQ : 1;
            int endQ = (year == currentYear) ? currentQuarter : 4;
            
            for (int q = startQ; q <= endQ; q++) {
                quarters.add(year + "Q" + q);
            }
        }
        
        return quarters;
    }
}
