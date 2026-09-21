package com.bimd.msgsim.domain.dto;

import java.util.List;

/** Broker x scenario matrix: one paired-rounds decision per scenario variant. */
public record SuiteResponse(List<VariantResult> variants, boolean leaderChanges, String summary) {

    /**
     * @param occupancyPct target occupation of the reference capacity this variant declares
     * @param ratePerSecond the rate the engine derived from it (occupation x capacity)
     * @param detail what the variant does besides the load (outage window, spike length...)
     */
    public record VariantResult(
            String variant,
            String label,
            int occupancyPct,
            int ratePerSecond,
            String detail,
            DecisionResponse decision) {
    }
}
