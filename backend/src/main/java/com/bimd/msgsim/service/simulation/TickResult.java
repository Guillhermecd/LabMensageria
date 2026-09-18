package com.bimd.msgsim.service.simulation;

public record TickResult(
        int second,
        int produced,
        int consumed,
        int failed,
        int dropped,
        int backlog,
        double utilization,
        int p50Ms,
        int p95Ms,
        int p99Ms,
        double capacity) {
}
