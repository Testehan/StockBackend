package com.testehan.finana.model.valuation;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record DcfOutput(
        BigDecimal equityValue,
        BigDecimal intrinsicValuePerShare,
        Double wacc,
        String verdict
) {
}
