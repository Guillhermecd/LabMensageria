package com.bimd.msgsim.service.real;

import com.bimd.msgsim.service.simulation.TickResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cumulative + per-tick counters fed by {@code RealSimulationService}'s {@link MessageProcessor}
 * callback, which runs on the broker's own consumer threads. {@link #buildTick} is called once a
 * second from the run loop's thread and drains the per-tick counters, so it must not run
 * concurrently with itself, but the record/increment methods are safe to call from any thread.
 */
class RealMetrics {

    private final AtomicLong totalProduced = new AtomicLong();
    private final AtomicLong totalOk = new AtomicLong();
    private final AtomicLong totalDlq = new AtomicLong();
    private final AtomicLong totalDropped = new AtomicLong();
    private final AtomicLong totalRetries = new AtomicLong();

    private final AtomicLong tickProduced = new AtomicLong();
    private final AtomicLong tickOk = new AtomicLong();
    private final AtomicLong tickFailed = new AtomicLong();
    private final AtomicLong tickDropped = new AtomicLong();
    private final ConcurrentLinkedQueue<Long> tickLatenciesMs = new ConcurrentLinkedQueue<>();

    void recordProduced(int count) {
        totalProduced.addAndGet(count);
        tickProduced.addAndGet(count);
    }

    void recordOk(long latencyMs) {
        totalOk.incrementAndGet();
        tickOk.incrementAndGet();
        tickLatenciesMs.add(latencyMs);
    }

    void recordRetry() {
        totalRetries.incrementAndGet();
        tickFailed.incrementAndGet();
    }

    void recordDlq() {
        totalDlq.incrementAndGet();
        tickFailed.incrementAndGet();
    }

    void recordDropped() {
        totalDropped.incrementAndGet();
        tickDropped.incrementAndGet();
    }

    long produced() {
        return totalProduced.get();
    }

    long ok() {
        return totalOk.get();
    }

    long dlq() {
        return totalDlq.get();
    }

    long dropped() {
        return totalDropped.get();
    }

    long retries() {
        return totalRetries.get();
    }

    TickResult buildTick(int second, long backlog, double capacity) {
        int produced = (int) tickProduced.getAndSet(0);
        int ok = (int) tickOk.getAndSet(0);
        int failed = (int) tickFailed.getAndSet(0);
        int dropped = (int) tickDropped.getAndSet(0);

        List<Long> latencies = new ArrayList<>();
        Long value;
        while ((value = tickLatenciesMs.poll()) != null) {
            latencies.add(value);
        }
        Collections.sort(latencies);

        double utilization = capacity > 0 ? Math.min(1, ok / capacity) : 0;
        return new TickResult(
                second, produced, ok, failed, dropped, (int) backlog, utilization,
                (int) percentile(latencies, 0.50), (int) percentile(latencies, 0.95), (int) percentile(latencies, 0.99),
                capacity);
    }

    private long percentile(List<Long> sortedLatencies, double p) {
        if (sortedLatencies.isEmpty()) {
            return 0;
        }
        int index = (int) Math.min(sortedLatencies.size() - 1, Math.floor(p * sortedLatencies.size()));
        return sortedLatencies.get(index);
    }
}
