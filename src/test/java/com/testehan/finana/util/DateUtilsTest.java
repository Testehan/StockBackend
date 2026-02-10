package com.testehan.finana.util;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DateUtilsTest {

    private final DateUtils dateUtils = new DateUtils();

    @Test
    void getDateQuarter_returnsCorrectQuarter() {
        assertEquals("2023Q1", dateUtils.getDateQuarter("2023-01-15"));
        assertEquals("2023Q2", dateUtils.getDateQuarter("2023-05-20"));
        assertEquals("2023Q3", dateUtils.getDateQuarter("2023-08-10"));
        assertEquals("2023Q4", dateUtils.getDateQuarter("2023-11-05"));
    }

    @Test
    void isRecent_returnsCorrectValue() {
        LocalDateTime now = LocalDateTime.now();
        assertTrue(dateUtils.isRecent(now.minusMinutes(5), 10));
        assertFalse(dateUtils.isRecent(now.minusMinutes(15), 10));
        assertFalse(dateUtils.isRecent(null, 10));
    }

    @Test
    void generateQuartersUpToCurrent_returnsCorrectList() {
        // This test might be sensitive to the current date when it was written, 
        // but it should work for a few years.
        List<String> quarters = dateUtils.generateQuartersUpToCurrent("2024Q1");
        assertFalse(quarters.isEmpty());
        assertEquals("2024Q1", quarters.get(0));
    }
}
