package com.bimd.msgsim.domain.dto;

import com.bimd.msgsim.domain.model.BrokerType;
import java.util.List;

/**
 * Simulation-backed broker comparison over N paired rounds (same seeds for every broker).
 * {@code tie} means the leader is not distinguishable from the runner-up; {@code decidedBy} names the
 * criterion that produced most of the (possibly meaningless) gap.
 */
public record DecisionResponse(
        int rounds,
        long masterSeed,
        double stabilityWeight,
        double latencyWeight,
        double lossWeight,
        double costWeight,
        double opsWeight,
        List<BrokerDecision> brokers,
        boolean tie,
        BrokerType leader,
        BrokerType runnerUp,
        double scoreGap,
        double leaderWinRate,
        String decidedBy,
        String verdict) {

    /** Points per criterion, in the order they are weighted. */
    public record Points(double stability, double latency, double loss, double cost, double ops) {
    }

    public record BrokerDecision(
            BrokerType broker,
            double scoreMedian,
            double scoreLow,
            double scoreHigh,
            Points points,
            double modelP50Ms,
            double simP50Ms,
            double modelP99Ms,
            double simP99Ms,
            double modelErrorPct,
            double simP99WorstMs,
            double peakBacklogMedian,
            double lossPctMedian,
            double scoreWorst,
            double simP99P95Ms,
            double peakBacklogWorst,
            /** Other brokers whose score is within the round-to-round dispersion of this one. */
            List<BrokerType> tiedWith,
            /** False when the analytical model and the simulation disagree beyond the tolerance. */
            boolean modelReliable,
            /** Present when the load exceeds the capacity: latency and backlog are then a function of run length. */
            Saturation saturation) {
    }

    /**
     * What replaces p50/p99/final backlog when load > capacity.
     * @param secondsToCeiling seconds until the broker hits its ceiling; null when it has none
     * @param ceilingWithinRun true when the simulation reached the ceiling, false when it is an extrapolation
     * @param failureMode DROP, PRODUCER_BLOCKED, INFLIGHT_EXHAUSTED or UNBOUNDED_BACKLOG
     * @param accumulatedCost estimated cost of the whole run at this load
     */
    public record Saturation(
            double ratePerSecond,
            double capacityPerSecond,
            double deficitPerSecond,
            Double secondsToCeiling,
            boolean ceilingWithinRun,
            String failureMode,
            double accumulatedCost,
            String recommendation) {
    }
}
