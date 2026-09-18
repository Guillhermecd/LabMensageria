package com.bimd.msgsim.service.report;

import com.bimd.msgsim.domain.model.BrokerType;

/** Closed-form estimate of how a broker would behave under a scenario's load — no simulation run needed. */
public record BrokerModel(
        BrokerType broker,
        int effectiveConsumers,
        double capacity,
        double ratio,
        boolean stable,
        double p50Ms,
        double p99Ms,
        long backlog,
        long loss,
        double lossPct,
        double cost,
        double opsScore,
        double idleConsumers) {
}
