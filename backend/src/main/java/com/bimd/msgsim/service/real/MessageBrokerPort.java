package com.bimd.msgsim.service.real;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;

/**
 * What {@code RealSimulationService} needs from a real broker — publish, consume, measure
 * depth, clean up. One implementation per broker (same Strategy shape as {@code BrokerBehavior}
 * for the simulated engine); adding Kafka/SQS later means a new adapter, not a change here.
 * Only RabbitMQ is implemented — see ADR 0006 for why Kafka/SQS are deferred.
 */
public interface MessageBrokerPort {

    BrokerType type();

    /** Declares a queue (and its dead-letter queue, if the scenario wants one) for this run. */
    void setUp(String queueName, Scenario scenario);

    void publish(String queueName, byte[] payload, int retryCount);

    /** Starts consuming with {@code concurrency} parallel workers; {@code processor} decides each message's fate. */
    void registerConsumer(String queueName, int concurrency, MessageProcessor processor);

    void stopConsumer(String queueName);

    /** Real backlog, read from the broker — not tracked in memory like the simulated engine does. */
    long queueDepth(String queueName);

    /** Purges and deletes the queue (and its DLQ). Always called, even when a run is stopped early —
     * a leftover queue would make the next run of the same scenario inherit a dirty backlog. */
    void tearDown(String queueName);

    boolean isHealthy();
}
