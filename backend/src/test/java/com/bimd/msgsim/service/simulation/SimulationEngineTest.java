package com.bimd.msgsim.service.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.service.simulation.broker.KafkaBehavior;
import com.bimd.msgsim.service.simulation.broker.RabbitMqBehavior;
import com.bimd.msgsim.service.simulation.broker.SqsBehavior;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationEngineTest {

    private static final long FIXED_SEED = 42L;

    private final SimulationEngine engine =
            new SimulationEngine(List.of(new KafkaBehavior(), new RabbitMqBehavior(), new SqsBehavior()));

    @Test
    void should_growBacklogLinearly_when_loadExceedsCapacity() {
        // 1000 msg/s in, ~1 msg/s effective capacity (1 consumer, 1000ms/msg): backlog can only grow
        Scenario scenario = scenario(BrokerType.KAFKA, 1000, 1, 1000, 0, 3, 10, true);
        scenario.setPartitions(1);

        SimulationState state = engine.runToCompletion(scenario, FIXED_SEED);

        List<TickResult> ticks = state.getTicks();
        assertThat(ticks.get(ticks.size() - 1).backlog()).isGreaterThan(ticks.get(0).backlog());
    }

    @Test
    void should_dropOldestMessages_when_rabbitQueueExceedsMaxLength() {
        Scenario scenario = scenario(BrokerType.RABBITMQ, 1000, 1, 1000, 0, 3, 10, true);
        scenario.setQueueCapacity(50);

        SimulationState state = engine.runToCompletion(scenario, FIXED_SEED);

        assertThat(state.getDropped()).isGreaterThan(0);
    }

    @Test
    void should_capConsumersAtPartitionCount_when_kafkaHasMoreConsumersThanPartitions() {
        Scenario scenario = scenario(BrokerType.KAFKA, 100, 10, 100, 0, 3, 10, true);
        scenario.setPartitions(3);

        int effective = new KafkaBehavior().effectiveConsumers(scenario);

        assertThat(effective).isEqualTo(3);
    }

    @Test
    void should_delayRetryByVisibilityTimeout_when_sqsMessageFails() {
        // half of attempts fail, so the first step must schedule a retry bucket
        // instead of returning the messages to the queue on the same tick
        Scenario scenario = scenario(BrokerType.SQS, 10, 5, 100, 50, 3, 20, true);
        scenario.setVisibilityTimeoutSeconds(5);

        SimulationState state = engine.newState(FIXED_SEED);
        engine.step(state, scenario);

        assertThat(state.retryBuckets).isNotEmpty();
        assertThat(state.retryBuckets.get(0).at()).isEqualTo(5);
    }

    private Scenario scenario(
            BrokerType broker,
            int ratePerSecond,
            int consumers,
            int processingMs,
            int failurePct,
            int maxRetries,
            int durationSeconds,
            boolean dlqEnabled) {
        Scenario scenario = new Scenario();
        scenario.setName("test");
        scenario.setBroker(broker);
        scenario.setRatePerSecond(ratePerSecond);
        scenario.setConsumers(consumers);
        scenario.setProcessingMs(processingMs);
        scenario.setFailurePct(BigDecimal.valueOf(failurePct));
        scenario.setMaxRetries(maxRetries);
        scenario.setMessageSizeKb(2);
        scenario.setDurationSeconds(durationSeconds);
        scenario.setDlqEnabled(dlqEnabled);
        scenario.setBurstEnabled(false);
        return scenario;
    }
}
