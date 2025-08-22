package com.testehan.finana.util;

import java.math.BigDecimal;

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
}
