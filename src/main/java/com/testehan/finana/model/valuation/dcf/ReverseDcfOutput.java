package com.testehan.finana.model.valuation.dcf;

import lombok.Builder;

@Builder
public record ReverseDcfOutput(
        Double impliedFCFGrowthRate
) {
}
