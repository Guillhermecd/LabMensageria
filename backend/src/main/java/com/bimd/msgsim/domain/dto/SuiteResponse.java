package com.bimd.msgsim.domain.dto;

import java.util.List;

/** Broker x scenario matrix: one paired-rounds decision per scenario variant. */
public record SuiteResponse(List<VariantResult> variants, boolean leaderChanges, String summary) {

    public record VariantResult(String variant, String label, DecisionResponse decision) {
    }
}
