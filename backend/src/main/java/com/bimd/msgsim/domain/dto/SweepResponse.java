package com.bimd.msgsim.domain.dto;

import com.bimd.msgsim.domain.model.BrokerType;
import java.util.List;

/** p99 latency versus utilisation: the load is set to a fraction of each broker's own capacity. */
public record SweepResponse(int rounds, long masterSeed, List<BrokerSweep> brokers) {

    public record BrokerSweep(BrokerType broker, double capacity, List<SweepPoint> points) {
    }

    public record SweepPoint(
            int occupancyPct, int ratePerSecond, double p99MedianMs, double p99P95Ms, double p99WorstMs,
            double peakBacklogMedian) {
    }
}
