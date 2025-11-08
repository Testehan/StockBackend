package com.testehan.finana.model.valuation.dcf;

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
