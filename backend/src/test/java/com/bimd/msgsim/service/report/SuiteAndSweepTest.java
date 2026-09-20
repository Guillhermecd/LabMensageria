package com.bimd.msgsim.service.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.bimd.msgsim.domain.dto.DecisionResponse.BrokerDecision;
import com.bimd.msgsim.domain.dto.SweepResponse;
import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.domain.model.ServiceProfile;
import com.bimd.msgsim.service.report.pricing.KafkaPricing;
import com.bimd.msgsim.service.report.pricing.RabbitMqPricing;
import com.bimd.msgsim.service.report.pricing.SqsPricing;
import com.bimd.msgsim.service.simulation.BatchService;
import com.bimd.msgsim.service.simulation.Disturbance;
import com.bimd.msgsim.service.simulation.SimulationEngine;
import com.bimd.msgsim.service.simulation.SimulationState;
import com.bimd.msgsim.service.simulation.TickResult;
import com.bimd.msgsim.service.simulation.broker.BrokerBehavior;
import com.bimd.msgsim.service.simulation.broker.KafkaBehavior;
import com.bimd.msgsim.service.simulation.broker.RabbitMqBehavior;
import com.bimd.msgsim.service.simulation.broker.SqsBehavior;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SuiteAndSweepTest {

    private final List<BrokerBehavior> behaviors =
            List.of(new KafkaBehavior(), new RabbitMqBehavior(), new SqsBehavior());
    private final AnalyticalQueueModel model = new AnalyticalQueueModel(
            behaviors, new CostEstimator(List.of(new KafkaPricing(), new RabbitMqPricing(), new SqsPricing())));
    private final SimulationEngine engine = new SimulationEngine(behaviors);
    private final DecisionService decisions = new DecisionService(null, null, engine, model);
    private final BatchService batches = new BatchService(null, null, engine);

    @Test
    void should_growBacklogDuringOutage_when_halfTheConsumersLeave() {
        Scenario scenario = scenario(BrokerType.RABBITMQ, 200, 4, 15);
        Disturbance outage = ScenarioVariant.CONSUMER_DOWN.disturbance(scenario);

        SimulationState healthy = engine.runToCompletion(scenario, 1L);
        SimulationState broken = engine.runToCompletion(scenario, 1L, outage);

        assertThat(peakBacklog(broken)).isGreaterThan(peakBacklog(healthy));
    }

    @Test
    void should_stallAllConsumption_when_kafkaRebalancesAfterConsumerLeaves() {
        Scenario kafka = scenario(BrokerType.KAFKA, 100, 4, 15);
        Scenario rabbit = scenario(BrokerType.RABBITMQ, 100, 4, 15);
        Disturbance outage = ScenarioVariant.CONSUMER_DOWN.disturbance(kafka);
        int start = (int) (kafka.getDurationSeconds() * Disturbance.OUTAGE_START);

        TickResult kafkaTick = engine.runToCompletion(kafka, 1L, outage).getTicks().get(start + 1);
        TickResult rabbitTick = engine.runToCompletion(rabbit, 1L, outage).getTicks().get(start + 1);

        assertThat(kafkaTick.consumed()).isZero();
        assertThat(rabbitTick.consumed()).isPositive();
    }

    @Test
    void should_produceOneDecisionPerVariant_when_runningSuiteInputs() {
        Scenario base = scenario(BrokerType.RABBITMQ, 100, 4, 15);

        for (ScenarioVariant variant : ScenarioVariant.values()) {
            var decision = decisions.decide(variant.apply(base), ScoreWeights.DEFAULT, 10, 3L, variant.disturbance(base));

            assertThat(decision.brokers()).hasSize(3);
        }
    }

    @Test
    void should_reportKafkaWorseWorstCaseThanRabbit_when_consumerDown() {
        Scenario base = scenario(BrokerType.RABBITMQ, 100, 4, 15);
        var decision = decisions.decide(
                ScenarioVariant.CONSUMER_DOWN.apply(base), ScoreWeights.DEFAULT, 20, 3L,
                ScenarioVariant.CONSUMER_DOWN.disturbance(base));

        BrokerDecision kafka = byBroker(decision.brokers(), BrokerType.KAFKA);
        BrokerDecision rabbit = byBroker(decision.brokers(), BrokerType.RABBITMQ);

        assertThat(kafka.peakBacklogMedian()).isGreaterThan(rabbit.peakBacklogMedian());
    }

    @Test
    void should_flagLeaderChange_when_variantsDisagree() {
        Scenario base = scenario(BrokerType.RABBITMQ, 100, 4, 15);
        var healthy = decisions.decide(base, ScoreWeights.DEFAULT, 10, 3L);
        var unstable = scenario(BrokerType.RABBITMQ, 500, 20, 15);
        unstable.setPartitions(1);
        var broken = decisions.decide(unstable, ScoreWeights.DEFAULT, 10, 3L);

        var suite = SuiteService.summarize(List.of(
                new com.bimd.msgsim.domain.dto.SuiteResponse.VariantResult("A", "a", forceLeader(healthy, BrokerType.RABBITMQ)),
                new com.bimd.msgsim.domain.dto.SuiteResponse.VariantResult("B", "b", forceLeader(broken, BrokerType.SQS))));

        assertThat(suite.leaderChanges()).isTrue();
    }

    @Test
    void should_showP99RisingWithOccupancy_when_sweeping() {
        SweepService sweeps = new SweepService(decisions, batches, model);
        SweepResponse sweep = sweeps.sweep(scenario(BrokerType.RABBITMQ, 100, 4, 15), 10, 5L);

        for (SweepResponse.BrokerSweep broker : sweep.brokers()) {
            List<SweepResponse.SweepPoint> points = broker.points();
            assertThat(points).hasSize(4);
            assertThat(points.get(3).p99MedianMs()).isGreaterThan(points.get(0).p99MedianMs());
        }
    }

    private static com.bimd.msgsim.domain.dto.DecisionResponse forceLeader(
            com.bimd.msgsim.domain.dto.DecisionResponse d, BrokerType leader) {
        return new com.bimd.msgsim.domain.dto.DecisionResponse(
                d.rounds(), d.masterSeed(), d.stabilityWeight(), d.latencyWeight(), d.lossWeight(), d.costWeight(),
                d.opsWeight(), d.brokers(), false, leader, d.runnerUp(), 20, 1.0, d.decidedBy(), d.verdict());
    }

    private static BrokerDecision byBroker(List<BrokerDecision> rows, BrokerType broker) {
        return rows.stream().filter(r -> r.broker() == broker).findFirst().orElseThrow();
    }

    private static double peakBacklog(SimulationState state) {
        return state.getTicks().stream().mapToDouble(TickResult::backlog).max().orElse(0);
    }

    private Scenario scenario(BrokerType broker, int rate, int consumers, int processingMs) {
        Scenario scenario = new Scenario();
        scenario.setName("test");
        scenario.setBroker(broker);
        scenario.setRatePerSecond(rate);
        scenario.setConsumers(consumers);
        scenario.setProcessingMs(processingMs);
        scenario.setFailurePct(BigDecimal.ONE);
        scenario.setMaxRetries(3);
        scenario.setMessageSizeKb(2);
        scenario.setDurationSeconds(120);
        scenario.setPartitions(6);
        scenario.setDlqEnabled(true);
        scenario.setBurstEnabled(false);
        scenario.setServiceProfile(ServiceProfile.EXPONENTIAL);
        return scenario;
    }
}
