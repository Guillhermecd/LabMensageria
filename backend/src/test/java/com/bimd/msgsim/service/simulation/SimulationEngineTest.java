package com.bimd.msgsim.service.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.domain.model.ServiceProfile;
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
        // half of attempts fail, so the first second must hide a message for the visibility timeout
        // instead of returning it to the queue right away
        Scenario scenario = scenario(BrokerType.SQS, 10, 5, 100, 50, 3, 20, true);
        scenario.setVisibilityTimeoutSeconds(5);

        SimulationState state = engine.newState(FIXED_SEED);
        engine.step(state, scenario);

        assertThat(state.nextRetryAtSeconds()).isGreaterThanOrEqualTo(5.0);
        assertThat(state.retryPending).isPositive();
    }

    @Test
    void should_conserveMessages_when_runningEveryBrokerWithFailuresAndOverflow() {
        for (BrokerType broker : BrokerType.values()) {
            Scenario scenario = scenario(broker, 300, 2, 20, 30, 2, 30, true);
            scenario.setQueueCapacity(200);
            scenario.setVisibilityTimeoutSeconds(4);

            SimulationState state = engine.runToCompletion(scenario, FIXED_SEED);

            assertThat(state.getProduced())
                    .isEqualTo(state.getOk() + state.getPending() + state.getDropped() + state.getDlq());
            assertThat(state.getWarnings()).isEmpty();
        }
    }

    @Test
    void should_keepArrivalsIdentical_when_onlyTheFailureRateChanges() {
        Scenario calm = scenario(BrokerType.RABBITMQ, 120, 4, 15, 0, 3, 30, true);
        Scenario flaky = scenario(BrokerType.RABBITMQ, 120, 4, 15, 40, 3, 30, true);

        List<Integer> calmArrivals = engine.runToCompletion(calm, FIXED_SEED).getTicks().stream()
                .map(TickResult::produced).toList();
        List<Integer> flakyArrivals = engine.runToCompletion(flaky, FIXED_SEED).getTicks().stream()
                .map(TickResult::produced).toList();

        assertThat(flakyArrivals).isEqualTo(calmArrivals);
    }

    @Test
    void should_giveBrokersTheSameArrivals_when_seedMatches() {
        Scenario kafka = scenario(BrokerType.KAFKA, 120, 4, 15, 1, 3, 30, true);
        kafka.setPartitions(8);
        Scenario rabbit = scenario(BrokerType.RABBITMQ, 120, 4, 15, 1, 3, 30, true);

        List<Integer> kafkaArrivals = engine.runToCompletion(kafka, FIXED_SEED).getTicks().stream()
                .map(TickResult::produced).toList();
        List<Integer> rabbitArrivals = engine.runToCompletion(rabbit, FIXED_SEED).getTicks().stream()
                .map(TickResult::produced).toList();

        assertThat(kafkaArrivals).isEqualTo(rabbitArrivals);
    }

    @Test
    void should_beReproducible_when_seedRepeats() {
        Scenario scenario = scenario(BrokerType.SQS, 80, 3, 20, 10, 3, 30, true);

        SimulationState a = engine.runToCompletion(scenario, FIXED_SEED);
        SimulationState b = engine.runToCompletion(scenario, FIXED_SEED);

        assertThat(a.getTicks()).isEqualTo(b.getTicks());
    }

    @Test
    void should_deliverExactlyProcessingTime_when_serviceIsConstantAndLoadIsLight() {
        Scenario scenario = scenario(BrokerType.RABBITMQ, 1, 10, 50, 0, 3, 200, true);
        scenario.setServiceProfile(ServiceProfile.CONSTANT);

        double[] latencies = engine.runToCompletion(scenario, FIXED_SEED).latenciesSince(0);

        // 50 ms service + 2 ms RabbitMQ overhead; 10 consumers at 1 msg/s never queue
        assertThat(latencies[0]).isEqualTo(52.0);
        assertThat(latencies[latencies.length - 1]).isEqualTo(52.0);
    }

    @Test
    void should_keepMeanServiceTime_when_profileIsHeavyTail() {
        java.util.SplittableRandom random = new java.util.SplittableRandom(7);
        int n = 400_000;
        double sum = 0;
        int slow = 0;
        for (int i = 0; i < n; i++) {
            double s = ServiceProfile.HEAVY_TAIL.sampleServiceMs(20, random.nextDouble());
            sum += s;
            if (s == 200.0) {
                slow++;
            }
        }

        assertThat(sum / n).isBetween(19.7, 20.3);
        assertThat(slow / (double) n).isBetween(0.048, 0.052);
    }

    /** Erlang-C mean wait for c servers, service rate mu (1/ms) and arrival rate lambda (1/ms). */
    private static double erlangCWaitMs(int c, double lambda, double mu) {
        double a = lambda / mu;
        double rho = a / c;
        double sum = 0;
        double term = 1;
        for (int k = 0; k < c; k++) {
            sum += term;
            term *= a / (k + 1);
        }
        double tail = term / (1 - rho);
        double waitProbability = tail / (sum + tail);
        return waitProbability / (c * mu - lambda);
    }

    @Test
    void should_matchErlangC_when_arrivalsAndServiceAreExponential() {
        int consumers = 2;
        int serviceMs = 10;
        for (double occupancy : new double[] {0.30, 0.50, 0.70, 0.85}) {
            int rate = (int) Math.round(occupancy * consumers * 1000.0 / serviceMs);
            Scenario scenario = scenario(BrokerType.RABBITMQ, rate, consumers, serviceMs, 0, 3, 3600, true);
            scenario.setServiceProfile(ServiceProfile.EXPONENTIAL);

            double simulated = 0;
            long[] seeds = {11L, 22L, 33L};
            for (long seed : seeds) {
                double[] latencies = engine.runToCompletion(scenario, seed).latenciesSince(60_000);
                // strip service (10 ms mean) and the 2 ms RabbitMQ overhead: what is left is the wait
                simulated += java.util.Arrays.stream(latencies).average().orElseThrow() - serviceMs - 2;
            }
            simulated /= seeds.length;
            double theory = erlangCWaitMs(consumers, rate / 1000.0, 1.0 / serviceMs);

            assertThat(simulated)
                    .as("mean wait at %.0f%% occupancy (theory %.3f ms)", occupancy * 100, theory)
                    .isCloseTo(theory, org.assertj.core.data.Percentage.withPercentage(5));
        }
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
