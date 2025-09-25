package com.testehan.finana.util;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

@Component
public class SafeParser {
    public BigDecimal parse(String value) {
        if (value == null || value.isEmpty() || value.equals("None")) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    public static Double tryParseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }
}
