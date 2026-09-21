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

class BrokerBehaviorTest {

    private static final long SEED = 7L;

    private final SimulationEngine engine =
            new SimulationEngine(List.of(new KafkaBehavior(), new RabbitMqBehavior(), new SqsBehavior()));

    /** 1000 msg/s into 1 consumer that handles 10 msg/s: a 100x overload for 60 s. */
    private Scenario overload(BrokerType broker) {
        Scenario scenario = scenario(broker, 1000, 1, 100, 0, 60);
        scenario.setPartitions(1);
        scenario.setMessageSizeKb(2);
        scenario.setRetentionMb(1); // 512 messages of 2 KB
        scenario.setHighWatermarkMb(1); // 512 messages of 2 KB
        return scenario;
    }

    @Test
    void should_stabiliseRabbitBacklogWithoutLoss_when_overloaded() {
        SimulationState state = engine.runToCompletion(overload(BrokerType.RABBITMQ), SEED);

        double peak = state.getTicks().stream().mapToDouble(TickResult::backlog).max().orElse(0);
        assertThat(peak).isLessThanOrEqualTo(512 + 1);
        assertThat(state.getDropped()).isZero();
        assertThat(state.getBlockedSeconds()).isGreaterThan(30);
        assertThat(state.getWarnings()).isEmpty();
    }

    @Test
    void should_dropOldestAtRetention_when_kafkaIsOverloaded() {
        SimulationState state = engine.runToCompletion(overload(BrokerType.KAFKA), SEED);

        assertThat(state.getDropped()).isGreaterThan(10_000);
        assertThat(state.getBlockedSeconds()).isZero();
        // retention is enforced once a second, so the log can overshoot by at most one second of arrivals
        double peak = state.getTicks().stream().mapToDouble(TickResult::backlog).max().orElse(0);
        assertThat(peak).isLessThanOrEqualTo(512 + 1);
    }

    @Test
    void should_produceDifferentOutcomes_when_kafkaAndRabbitFaceTheSameOverload() {
        SimulationState kafka = engine.runToCompletion(overload(BrokerType.KAFKA), SEED);
        SimulationState rabbit = engine.runToCompletion(overload(BrokerType.RABBITMQ), SEED);

        assertThat(kafka.getDropped()).isGreaterThan(rabbit.getDropped());
        assertThat(kafka.getProduced()).isGreaterThan(rabbit.getProduced());
        assertThat(kafka.getTicks()).isNotEqualTo(rabbit.getTicks());
    }

    @Test
    void should_dropMoreAtRetention_when_messagesAreBigger() {
        Scenario small = overload(BrokerType.KAFKA);
        Scenario big = overload(BrokerType.KAFKA);
        big.setMessageSizeKb(16); // only 64 messages fit in 1 MB

        long droppedSmall = engine.runToCompletion(small, SEED).getDropped();
        long droppedBig = engine.runToCompletion(big, SEED).getDropped();

        assertThat(droppedBig).isGreaterThan(droppedSmall);
    }

    @Test
    void should_reduceKafkaThroughput_when_partitionsAreFewerThanConsumers() {
        Scenario few = scenario(BrokerType.KAFKA, 500, 8, 20, 0, 30);
        few.setPartitions(2);
        Scenario many = scenario(BrokerType.KAFKA, 500, 8, 20, 0, 30);
        many.setPartitions(8);

        assertThat(engine.runToCompletion(few, SEED).getOk())
                .isLessThan(engine.runToCompletion(many, SEED).getOk());
    }

    @Test
    void should_deadLetterAfterMaxRetries_when_everyAttemptFails() {
        Scenario scenario = scenario(BrokerType.RABBITMQ, 20, 4, 10, 100, 20);
        scenario.setMaxRetries(2);

        SimulationState state = engine.runToCompletion(scenario, SEED);

        assertThat(state.getOk()).isZero();
        assertThat(state.getDlq()).isPositive();
        // every dead-lettered message was tried three times: the original plus two retries
        assertThat(state.getRetries()).isBetween(2 * state.getDlq(), 2 * state.getDlq() + 2 * state.getPending());
    }

    @Test
    void should_discardAfterMaxRetries_when_dlqIsDisabled() {
        Scenario scenario = scenario(BrokerType.RABBITMQ, 20, 4, 10, 100, 20);
        scenario.setMaxRetries(1);
        scenario.setDlqEnabled(false);

        SimulationState state = engine.runToCompletion(scenario, SEED);

        assertThat(state.getDlq()).isZero();
        assertThat(state.getDropped()).isPositive();
    }

    @Test
    void should_capThroughputAtInflightCeiling_when_sqsHasFewSlots() {
        Scenario scenario = scenario(BrokerType.SQS, 500, 10, 100, 0, 30);
        scenario.setInflightMax(2); // 2 concurrent messages => at most 20 msg/s

        SimulationState state = engine.runToCompletion(scenario, SEED);

        assertThat(state.getOk()).isBetween(300L, 700L);
    }

    @Test
    void should_shrinkRabbitParallelism_when_prefetchIsOne() {
        Scenario tuned = scenario(BrokerType.RABBITMQ, 100, 100, 2, 0, 10);
        tuned.setPrefetch(1);
        Scenario roomy = scenario(BrokerType.RABBITMQ, 100, 100, 2, 0, 10);
        roomy.setPrefetch(250);
        RabbitMqBehavior rabbit = new RabbitMqBehavior();

        assertThat(rabbit.effectiveConsumers(tuned)).isLessThan(rabbit.effectiveConsumers(roomy));
        assertThat(rabbit.effectiveConsumers(roomy)).isGreaterThanOrEqualTo(99);
    }

    @Test
    void should_fallBackToDefaults_when_brokerParametersAreNull() {
        Scenario scenario = scenario(BrokerType.RABBITMQ, 100, 4, 15, 0, 20);

        for (BrokerType broker : BrokerType.values()) {
            scenario.setBroker(broker);
            SimulationState state = engine.runToCompletion(scenario, SEED);
            assertThat(state.getOk()).isPositive();
        }
    }

    private Scenario scenario(BrokerType broker, int rate, int consumers, int processingMs, int failurePct, int duration) {
        Scenario scenario = new Scenario();
        scenario.setName("test");
        scenario.setBroker(broker);
        scenario.setRatePerSecond(rate);
        scenario.setConsumers(consumers);
        scenario.setProcessingMs(processingMs);
        scenario.setFailurePct(BigDecimal.valueOf(failurePct));
        scenario.setMaxRetries(3);
        scenario.setMessageSizeKb(2);
        scenario.setDurationSeconds(duration);
        scenario.setDlqEnabled(true);
        scenario.setBurstEnabled(false);
        return scenario;
    }
}
