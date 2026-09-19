package com.bimd.msgsim.service.simulation.broker;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.service.simulation.SimulationState;

/**
 * Broker-specific rules applied once per simulated second. Adding a new broker
 * means adding a new implementation here — {@code SimulationEngine} never changes (Open/Closed).
 */
public interface BrokerBehavior {

    BrokerType type();

    /** Kafka caps parallelism at partition count; other brokers use every consumer. */
    int effectiveConsumers(Scenario scenario);

    /** Applies queue-overflow rules (only RabbitMQ drops messages); returns how many were dropped this tick. */
    int applyOverflow(SimulationState state, Scenario scenario, int second);

    /** Seconds before a failed message is retried (SQS waits for the visibility timeout; others retry next tick). */
    int retryDelaySeconds(Scenario scenario);

    /** Fixed per-message latency overhead used in the p50/p95/p99 estimate. */
    int latencyOverheadMs();

    /** Message shown the first time a message reaches the DLQ for this run. */
    String firstDlqMessage();

    /** Retry delay (ms) used by the closed-form analytical model — not the tick-by-tick engine. */
    int analyticalRetryDelayMs(Scenario scenario);

    /** Relative operational simplicity (0..1, higher = simpler to run) used by the trade-off score. */
    double operationalSimplicityScore();
}
