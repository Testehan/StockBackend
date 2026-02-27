package com.testehan.finana.util;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class SafeParserTest {

    private final SafeParser safeParser = new SafeParser();

    @Test
    void parse_returnsBigDecimal() {
        assertEquals(new BigDecimal("100.5"), safeParser.parse("100.5"));
        assertEquals(BigDecimal.ZERO, safeParser.parse(null));
        assertEquals(BigDecimal.ZERO, safeParser.parse(""));
        assertEquals(BigDecimal.ZERO, safeParser.parse("None"));
        assertEquals(BigDecimal.ZERO, safeParser.parse("abc"));
    }

    @Test
    void tryParseDouble_returnsDoubleOrNull() {
        assertEquals(10.5, SafeParser.tryParseDouble("10.5"));
        assertNull(SafeParser.tryParseDouble(null));
        assertNull(SafeParser.tryParseDouble("abc"));
    }
}
