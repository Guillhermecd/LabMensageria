package com.bimd.msgsim.domain.model;

/**
 * How per-message service time varies around {@code processingMs} (the mean stays the same, so raw
 * capacity is unchanged). {@link #cv2()} is the squared coefficient of variation feeding the
 * Allen-Cunneen wait approximation; the tail factors scale the p95/p99 service component.
 * A constant service time is the best case of queueing theory — real consumers pause on GC, DB
 * stalls and payload size, so anything but CONSTANT is the realistic default.
 */
public enum ServiceProfile {
    /** Every message takes exactly the mean. */
    CONSTANT(0.0, 1.9, 2.8),
    /** Memoryless: many fast messages, a few slow ones (classic M/M/c assumption). */
    EXPONENTIAL(1.0, 3.0, 4.6),
    /** 5% of messages take 10x the typical time (e.g. GC pause, cold cache, DB lock). */
    HEAVY_TAIL(1.83, 5.0, 10.0);

    private final double cv2;
    private final double p95Factor;
    private final double p99Factor;

    ServiceProfile(double cv2, double p95Factor, double p99Factor) {
        this.cv2 = cv2;
        this.p95Factor = p95Factor;
        this.p99Factor = p99Factor;
    }

    public double cv2() {
        return cv2;
    }

    public double p95Factor() {
        return p95Factor;
    }

    public double p99Factor() {
        return p99Factor;
    }

    /**
     * Mean queueing delay in ms at utilisation {@code rho} with {@code servers} consumers
     * (Allen-Cunneen, Poisson arrivals). Grows without bound as rho approaches 1, so it is
     * capped just below saturation — beyond that the backlog term dominates.
     */
    public double queueingWaitMs(double rho, int servers, double serviceMs) {
        double r = Math.min(Math.max(rho, 0), 0.99);
        double erlang = Math.pow(r, Math.sqrt(2.0 * (servers + 1)) - 1) / (servers * (1 - r));
        return serviceMs * erlang * (1 + cv2) / 2;
    }
}
