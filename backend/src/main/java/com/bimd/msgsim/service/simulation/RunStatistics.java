package com.bimd.msgsim.service.simulation;

import com.bimd.msgsim.domain.model.Scenario;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Steady-state summary of one simulated run. Ticks before {@link #warmupSeconds(int)} are dropped:
 * the queue starts empty, so nothing waits yet and the early latencies flatter the system.
 * Latencies are percentiles over the individual messages delivered after warmup (queue wait + service
 * + broker overhead), falling back to the per-tick series only when nothing was delivered.
 */
public record RunStatistics(
        int warmupSeconds,
        double p50Ms,
        double p95Ms,
        double p99Ms,
        double peakBacklog,
        double throughput,
        double rawCapacity,
        double usefulCapacity,
        double lossPct,
        double endBacklog) {

    public static int warmupSeconds(int durationSeconds) {
        return Math.min(Math.max(5, durationSeconds / 10), durationSeconds / 2);
    }

    public static RunStatistics of(SimulationState state, Scenario scenario) {
        int warmup = warmupSeconds(scenario.getDurationSeconds());
        List<TickResult> all = state.getTicks();
        List<TickResult> steady = all.stream().filter(t -> t.second() >= warmup).toList();
        List<TickResult> ticks = steady.isEmpty() ? all : steady;

        double consumed = ticks.stream().mapToDouble(TickResult::consumed).sum();
        double failed = ticks.stream().mapToDouble(TickResult::failed).sum();
        double attempts = consumed + failed;
        double rawCapacity = mean(ticks, TickResult::capacity);
        double usefulShare = attempts > 0 ? consumed / attempts : 1;
        double lossPct = state.getProduced() > 0
                ? 100.0 * (state.getDlq() + state.getDropped()) / state.getProduced()
                : 0;

        double[] delivered = state.latenciesSince(warmup * 1000.0);
        boolean measured = delivered.length > 0;

        return new RunStatistics(
                warmup,
                measured ? Percentile.of(delivered, delivered.length, 0.50) : mean(ticks, TickResult::p50Ms),
                measured ? Percentile.of(delivered, delivered.length, 0.95) : mean(ticks, TickResult::p95Ms),
                measured ? Percentile.of(delivered, delivered.length, 0.99) : mean(ticks, TickResult::p99Ms),
                ticks.stream().mapToDouble(TickResult::backlog).max().orElse(0),
                mean(ticks, TickResult::consumed),
                rawCapacity,
                rawCapacity * usefulShare,
                lossPct,
                all.isEmpty() ? 0 : all.get(all.size() - 1).backlog());
    }

    private static double mean(List<TickResult> ticks, ToDoubleFunction<TickResult> field) {
        return ticks.stream().mapToDouble(field).average().orElse(0);
    }
}
