package com.testehan.finana.model.valuation;

import lombok.Builder;

@Builder
public record ReverseDcfOutput(
        Double impliedFCFGrowthRate
) {
}
