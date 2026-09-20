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
            double lossPctMedian) {
    }
}
