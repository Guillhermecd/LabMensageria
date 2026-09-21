package com.bimd.msgsim.service.simulation.broker;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.service.simulation.SimulationState;

/**
 * The four decision points where brokers differ; everything else in {@code SimulationEngine} is
 * generic. Adding a new broker means adding a new implementation here — the engine never changes
 * (Open/Closed). Broker parameters left null on a scenario fall back to the defaults declared by
 * each implementation, so one scenario can be replayed against every broker.
 */
public interface BrokerBehavior {

    BrokerType type();

    /** Publish path: accept the message, block the producer, or reject it. Called for every arrival. */
    Admission admit(SimulationState state, Scenario scenario);

    /** How many consumers can process right now (partitions, prefetch, in-flight ceiling). */
    int parallelism(SimulationState state, Scenario scenario);

    /** Delay (ms) until a failed message becomes available again; 0 means immediately. */
    long retryDelayMs(SimulationState state, Scenario scenario, int failures);

    /** Aging sweep, run every simulated second: drops messages that aged out; returns how many. */
    int sweep(SimulationState state, Scenario scenario, int second);

    /** Steady-state parallelism ignoring transient state; used by the closed-form analytical model. */
    int effectiveConsumers(Scenario scenario);

    /** Fixed per-message latency overhead added to every delivered message. */
    int latencyOverheadMs();

    /**
     * Seconds all consumption stops when a consumer leaves the group. Kafka rebalances the whole
     * group (eager assignment); RabbitMQ and SQS simply redeliver to the remaining consumers.
     */
    default int failoverPauseSeconds() {
        return 0;
    }

    /** Message shown the first time a message reaches the DLQ for this run. */
    String firstDlqMessage();

    /** Retry delay (ms) used by the closed-form analytical model. */
    int analyticalRetryDelayMs(Scenario scenario);

    /** Relative operational simplicity (0..1, higher = simpler to run) used by the trade-off score. */
    double operationalSimplicityScore();

    /** How many messages of this scenario's size fit in {@code megabytes}. */
    static long messagesThatFit(Scenario scenario, int megabytes) {
        return megabytes * 1024L / Math.max(1, scenario.getMessageSizeKb());
    }
}
