package com.bimd.msgsim.service.simulation;

import java.util.Comparator;

/**
 * A future happening in the simulated clock (milliseconds). The declaration order of {@link Kind}
 * breaks ties between events at the same instant: completions free a consumer before arrivals
 * are dispatched, and the per-second sweep runs last so it sees everything that happened in the second.
 */
final class SimEvent {

    enum Kind { COMPLETION, REAPPEAR, ARRIVAL, SWEEP }

    static final Comparator<SimEvent> ORDER = Comparator
            .comparingDouble((SimEvent e) -> e.timeMs)
            .thenComparing(e -> e.kind)
            .thenComparingLong(e -> e.seq);

    final double timeMs;
    final Kind kind;
    final long seq;
    /** Instant the message first entered the system (COMPLETION / REAPPEAR only). */
    final double arrivalMs;
    /** Failed attempts so far (COMPLETION / REAPPEAR only). */
    final int failures;

    SimEvent(double timeMs, Kind kind, long seq, double arrivalMs, int failures) {
        this.timeMs = timeMs;
        this.kind = kind;
        this.seq = seq;
        this.arrivalMs = arrivalMs;
        this.failures = failures;
    }
}
