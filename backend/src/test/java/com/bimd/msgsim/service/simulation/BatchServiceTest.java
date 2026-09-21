package com.bimd.msgsim.service.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bimd.msgsim.domain.dto.BatchSummaryResponse;
import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.service.simulation.broker.KafkaBehavior;
import com.bimd.msgsim.service.simulation.broker.RabbitMqBehavior;
import com.bimd.msgsim.service.simulation.broker.SqsBehavior;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchServiceTest {

    private final SimulationEngine engine =
            new SimulationEngine(List.of(new KafkaBehavior(), new RabbitMqBehavior(), new SqsBehavior()));
    private final BatchService service = new BatchService(null, null, engine);

    @Test
    void should_produceIdenticalSummary_when_sameMasterSeed() {
        Scenario scenario = scenario(50, 3, 15, 1, 60);

        BatchSummaryResponse a = service.summarize(scenario, 20, 7L);
        BatchSummaryResponse b = service.summarize(scenario, 20, 7L);

        assertThat(a).isEqualTo(b);
    }

    @Test
    void should_orderRangeFromMinToWorst_when_roundsVary() {
        BatchSummaryResponse summary = service.summarize(scenario(50, 3, 15, 1, 60), 30, 1L);

        assertThat(summary.p99Ms().min()).isLessThanOrEqualTo(summary.p99Ms().median());
        assertThat(summary.p99Ms().median()).isLessThanOrEqualTo(summary.p99Ms().p95());
        assertThat(summary.p99Ms().p95()).isLessThanOrEqualTo(summary.p99Ms().worst());
    }

    @Test
    void should_reproduceWorstRound_when_replayingItsSeed() {
        Scenario scenario = scenario(50, 3, 15, 1, 60);
        BatchSummaryResponse summary = service.summarize(scenario, 30, 3L);

        RunStatistics replay = RunStatistics.of(engine.runToCompletion(scenario, summary.p99Ms().worstSeed()), scenario);

        assertThat(replay.p99Ms()).isEqualTo(summary.p99Ms().worst());
    }

    @Test
    void should_excludeWarmupTicks_when_computingStatistics() {
        Scenario scenario = scenario(50, 3, 15, 1, 60);
        SimulationState state = engine.runToCompletion(scenario, 5L);
        int warmup = RunStatistics.warmupSeconds(60);

        double[] steady = state.latenciesSince(warmup * 1000.0);

        assertThat(steady.length).isLessThan(state.latenciesSince(0).length);
        assertThat(RunStatistics.of(state, scenario).p99Ms()).isEqualTo(Percentile.of(steady, steady.length, 0.99));
        assertThat(warmup).isEqualTo(6);
    }

    @Test
    void should_reportUsefulCapacityBelowRaw_when_messagesFail() {
        RunStatistics stats = RunStatistics.of(engine.runToCompletion(scenario(50, 3, 15, 10, 60), 5L),
                scenario(50, 3, 15, 10, 60));

        assertThat(stats.usefulCapacity()).isLessThan(stats.rawCapacity());
    }

    private Scenario scenario(int rate, int consumers, int processingMs, int failurePct, int duration) {
        Scenario scenario = new Scenario();
        scenario.setName("test");
        scenario.setBroker(BrokerType.RABBITMQ);
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
