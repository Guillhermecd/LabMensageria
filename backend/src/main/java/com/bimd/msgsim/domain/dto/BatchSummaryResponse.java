package com.bimd.msgsim.domain.dto;

import java.util.UUID;

/**
 * Result of N seeded rounds of the same scenario. {@code worstSeed} of each metric can be replayed
 * through {@code POST /scenarios/{id}/runs?seed=} to investigate that round.
 */
public record BatchSummaryResponse(
        UUID scenarioId,
        int rounds,
        long masterSeed,
        int warmupSeconds,
        MetricRange p50Ms,
        MetricRange p95Ms,
        MetricRange p99Ms,
        MetricRange peakBacklog,
        MetricRange lossPct,
        double rawCapacity,
        double usefulCapacity) {
}
