package com.bimd.msgsim.service.simulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.SplittableRandom;

/**
 * Mutable per-run state of the discrete-event {@link SimulationEngine}: the future-event heap, the
 * waiting messages and the counters. Not persisted directly — {@code SimulationRunService} maps
 * {@link #ticks} and {@link #events} to JPA entities once the run finishes.
 *
 * <p>Each source of randomness has its own stream, all derived from the single seed, so changing
 * the failure rate never shifts the arrival instants and two brokers run with the same seed
 * receive exactly the same messages at exactly the same times.
 */
public class SimulationState {

    final SplittableRandom arrivalRandom;
    final SplittableRandom serviceRandom;
    final SplittableRandom failureRandom;
    final Disturbance disturbance;
    final List<TickResult> ticks = new ArrayList<>();
    final List<EventResult> events = new ArrayList<>();
    final List<String> warnings = new ArrayList<>();

    final PriorityQueue<SimEvent> heap = new PriorityQueue<>(SimEvent.ORDER);
    final MessageQueue waiting = new MessageQueue();
    long eventSeq;

    /** Simulated clock in milliseconds; jumps to the next event instead of ticking. */
    double nowMs;
    boolean started;
    int busy;
    int servers;
    /** Messages that failed and are hidden until their retry delay elapses. */
    int retryPending;
    /** Messages currently inside the system (waiting + in service + hidden), integrated for Little's law. */
    int inSystem;
    double lastAccumMs;
    double busyMsSecond;
    double inSystemArea;
    double sojournSum;

    long produced;
    long ok;
    long dlq;
    long dropped;
    long retries;
    int arrivalsSecond;
    int okSecond;
    int failedSecond;
    int droppedSecond;
    double[] secondLatencies = new double[256];
    int secondLatencyCount;

    double[] latencies = new double[1024];
    double[] latencyAt = new double[1024];
    int latencyCount;

    int satStreak;
    boolean saturated;
    boolean dlqSeen;
    boolean dropSeen;
    boolean lagWarn;
    boolean burstSeen;
    boolean burstEnd;
    boolean done;
    int t;

    public SimulationState(long seed) {
        this(seed, Disturbance.NONE);
    }

    public SimulationState(long seed, Disturbance disturbance) {
        this.disturbance = disturbance;
        SplittableRandom root = new SplittableRandom(seed);
        this.arrivalRandom = root.split();
        this.serviceRandom = root.split();
        this.failureRandom = root.split();
    }

    /** Waiting (visible, not yet being processed) messages. */
    public double getQueue() {
        return waiting.size();
    }

    /** Drops the oldest waiting messages (drop-head); returns how many were actually removed. */
    public int dropOldest(int count) {
        int removed = Math.min(count, waiting.size());
        for (int i = 0; i < removed; i++) {
            sojournSum += nowMs - waiting.poll();
        }
        inSystem -= removed;
        dropped += removed;
        droppedSecond += removed;
        return removed;
    }

    public boolean isDropSeen() {
        return dropSeen;
    }

    public void setDropSeen(boolean dropSeen) {
        this.dropSeen = dropSeen;
    }

    public void addEvent(EventResult event) {
        this.events.add(event);
    }

    /** Non-fatal accounting warnings (e.g. Little's law off by more than 2%). */
    public List<String> getWarnings() {
        return warnings;
    }

    void recordLatency(double latencyMs) {
        if (secondLatencyCount == secondLatencies.length) {
            secondLatencies = Arrays.copyOf(secondLatencies, secondLatencyCount * 2);
        }
        secondLatencies[secondLatencyCount++] = latencyMs;
        if (latencyCount == latencies.length) {
            latencies = Arrays.copyOf(latencies, latencyCount * 2);
            latencyAt = Arrays.copyOf(latencyAt, latencyCount * 2);
        }
        latencies[latencyCount] = latencyMs;
        latencyAt[latencyCount++] = nowMs;
    }

    /** Ascending latencies (ms) of the messages delivered at or after {@code sinceMs}. */
    double[] latenciesSince(double sinceMs) {
        double[] out = new double[latencyCount];
        int n = 0;
        for (int i = 0; i < latencyCount; i++) {
            if (latencyAt[i] >= sinceMs) {
                out[n++] = latencies[i];
            }
        }
        out = Arrays.copyOf(out, n);
        Arrays.sort(out);
        return out;
    }

    public List<TickResult> getTicks() {
        return ticks;
    }

    public List<EventResult> getEvents() {
        return events;
    }

    public boolean isDone() {
        return done;
    }

    public long getProduced() {
        return produced;
    }

    public long getOk() {
        return ok;
    }

    public long getDlq() {
        return dlq;
    }

    public long getDropped() {
        return dropped;
    }

    public long getRetries() {
        return retries;
    }

    /** Messages accepted but neither delivered, dead-lettered nor dropped yet. */
    public long getPending() {
        return waiting.size() + busy + retryPending;
    }

    /** Seconds until the next hidden retry reappears, or -1 when none is scheduled. */
    public double nextRetryAtSeconds() {
        return heap.stream()
                .filter(e -> e.kind == SimEvent.Kind.REAPPEAR)
                .mapToDouble(e -> e.timeMs / 1000.0)
                .min()
                .orElse(-1);
    }
}
