package com.bimd.msgsim.service.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bimd.msgsim.domain.dto.DecisionResponse;
import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.domain.model.ServiceProfile;
import com.bimd.msgsim.exception.BusinessException;
import com.bimd.msgsim.service.report.pricing.KafkaPricing;
import com.bimd.msgsim.service.report.pricing.RabbitMqPricing;
import com.bimd.msgsim.service.report.pricing.SqsPricing;
import com.bimd.msgsim.service.simulation.SimulationEngine;
import com.bimd.msgsim.service.simulation.broker.KafkaBehavior;
import com.bimd.msgsim.service.simulation.broker.RabbitMqBehavior;
import com.bimd.msgsim.service.simulation.broker.SqsBehavior;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DecisionServiceTest {

    private final List<com.bimd.msgsim.service.simulation.broker.BrokerBehavior> behaviors =
            List.of(new KafkaBehavior(), new RabbitMqBehavior(), new SqsBehavior());
    private final AnalyticalQueueModel model = new AnalyticalQueueModel(
            behaviors, new CostEstimator(List.of(new KafkaPricing(), new RabbitMqPricing(), new SqsPricing())));
    private final SimulationEngine engine = new SimulationEngine(behaviors);
    private final DecisionService service = new DecisionService(null, null, engine, model);

    @Test
    void should_waitHalfAsLong_when_serviceTimeIsConstantInsteadOfExponential() {
        // M/D/1 waits half of M/M/1 at the same utilisation
        double constant = ServiceProfile.CONSTANT.queueingWaitMs(0.8, 1, 15);
        double exponential = ServiceProfile.EXPONENTIAL.queueingWaitMs(0.8, 1, 15);

        assertThat(constant).isCloseTo(exponential / 2, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void should_raiseP99_when_serviceTimeHasHeavyTail() {
        double constant = model.compute(scenario(ServiceProfile.CONSTANT, 200), BrokerType.RABBITMQ).p99Ms();
        double heavy = model.compute(scenario(ServiceProfile.HEAVY_TAIL, 200), BrokerType.RABBITMQ).p99Ms();

        assertThat(heavy).isGreaterThan(constant * 2);
    }

    @Test
    void should_declareTechnicalTieAndNameCostTiebreaker_when_brokersPerformAlike() {
        DecisionResponse decision = service.decide(scenario(ServiceProfile.EXPONENTIAL, 200), ScoreWeights.DEFAULT, 30, 1L);

        // RabbitMQ and Kafka perform alike here; only the per-run cost separates them
        assertThat(decision.scoreGap()).isLessThan(DecisionService.MIN_MEANINGFUL_GAP * 4);
        assertThat(decision.verdict()).isNotBlank();
        assertThat(decision.decidedBy()).isEqualTo("custo");
        assertThat(decision.tie()).isTrue();
        assertThat(decision.verdict()).contains("Empate técnico").contains("desempate seria custo");
    }

    @Test
    void should_notDeclareTie_when_oneBrokerIsUnstable() {
        // Kafka with 1 partition cannot use the 20 consumers: it saturates, the others do not
        Scenario scenario = scenario(ServiceProfile.EXPONENTIAL, 500);
        scenario.setConsumers(20);
        scenario.setPartitions(1);

        DecisionResponse decision = service.decide(scenario, ScoreWeights.DEFAULT, 30, 1L);

        assertThat(decision.tie()).isFalse();
        assertThat(decision.leader()).isNotEqualTo(BrokerType.KAFKA);
        assertThat(decision.leaderWinRate()).isEqualTo(1.0);
    }

    @Test
    void should_beReproducible_when_sameSeed() {
        Scenario scenario = scenario(ServiceProfile.EXPONENTIAL, 200);

        assertThat(service.decide(scenario, ScoreWeights.DEFAULT, 20, 9L))
                .isEqualTo(service.decide(scenario, ScoreWeights.DEFAULT, 20, 9L));
    }

    @Test
    void should_rescaleWeights_when_theyDoNotSumToOne() {
        ScoreWeights weights = new ScoreWeights(35, 25, 20, 10, 10).normalized();

        assertThat(weights.stability()).isEqualTo(0.35);
    }

    @Test
    void should_rejectWeights_when_allZeroOrNegative() {
        assertThatThrownBy(() -> new ScoreWeights(0, 0, 0, 0, 0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new ScoreWeights(-1, 1, 1, 1, 1)).isInstanceOf(BusinessException.class);
    }

    @Test
    void should_scoreSmallLatencyGapAsSmallScoreGap_when_usingRatios() {
        // 3% slower p99 must cost ~3% of the latency weight, not the whole of it (min-max would)
        var points = ScoreCalculator.score(List.of(
                new ScoreCalculator.Inputs(BrokerType.KAFKA, 1, 100, 0, 1, 1),
                new ScoreCalculator.Inputs(BrokerType.RABBITMQ, 1, 103, 0, 1, 1)), ScoreWeights.DEFAULT);

        double gap = points.get(BrokerType.KAFKA).total() - points.get(BrokerType.RABBITMQ).total();

        assertThat(gap).isLessThan(1.0);
    }

    private Scenario scenario(ServiceProfile profile, int rate) {
        Scenario scenario = new Scenario();
        scenario.setName("test");
        scenario.setBroker(BrokerType.RABBITMQ);
        scenario.setRatePerSecond(rate);
        scenario.setConsumers(4);
        scenario.setProcessingMs(15);
        scenario.setFailurePct(BigDecimal.ONE);
        scenario.setMaxRetries(3);
        scenario.setMessageSizeKb(2);
        scenario.setDurationSeconds(120);
        scenario.setPartitions(6);
        scenario.setDlqEnabled(true);
        scenario.setBurstEnabled(false);
        scenario.setServiceProfile(profile);
        return scenario;
    }
}
