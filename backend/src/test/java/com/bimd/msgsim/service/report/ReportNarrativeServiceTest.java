package com.bimd.msgsim.service.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.domain.model.SimulationRun;
import com.bimd.msgsim.service.report.pricing.KafkaPricing;
import com.bimd.msgsim.service.report.pricing.RabbitMqPricing;
import com.bimd.msgsim.service.report.pricing.SqsPricing;
import com.bimd.msgsim.service.simulation.broker.KafkaBehavior;
import com.bimd.msgsim.service.simulation.broker.RabbitMqBehavior;
import com.bimd.msgsim.service.simulation.broker.SqsBehavior;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportNarrativeServiceTest {

    private static final Map<BrokerType, String> NAMES =
            Map.of(BrokerType.KAFKA, "Kafka", BrokerType.RABBITMQ, "RabbitMQ", BrokerType.SQS, "SQS/SNS");

    private final CostEstimator costEstimator =
            new CostEstimator(List.of(new KafkaPricing(), new RabbitMqPricing(), new SqsPricing()));
    private final AnalyticalQueueModel model = new AnalyticalQueueModel(
            List.of(new KafkaBehavior(), new RabbitMqBehavior(), new SqsBehavior()), costEstimator);
    private final BrokerTradeoffService tradeoffService = new BrokerTradeoffService(model);
    private final ReportNarrativeService narrativeService =
            new ReportNarrativeService(List.of(new KafkaBehavior(), new RabbitMqBehavior(), new SqsBehavior()));

    @Test
    void should_citeWinningBroker_when_generatingConclusion() {
        Scenario scenario = scenario(BrokerType.KAFKA, 100, 20, 50);
        scenario.setPartitions(2); // constrained enough that Kafka should not win

        TradeoffResult tradeoffs = tradeoffService.evaluate(scenario);
        SimulationRun run = new SimulationRun();

        List<String> conclusion = narrativeService.conclusion(scenario, tradeoffs, run, List.of());

        String winnerName = NAMES.get(tradeoffs.best().model().broker());
        assertThat(conclusion.get(0)).contains(winnerName);
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
        scenario.setDlqEnabled(true);
        scenario.setBurstEnabled(false);
        return scenario;
    }
}
