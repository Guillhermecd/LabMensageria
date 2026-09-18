package com.bimd.msgsim.service.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.service.report.pricing.KafkaPricing;
import com.bimd.msgsim.service.report.pricing.RabbitMqPricing;
import com.bimd.msgsim.service.report.pricing.SqsPricing;
import com.bimd.msgsim.service.simulation.broker.KafkaBehavior;
import com.bimd.msgsim.service.simulation.broker.RabbitMqBehavior;
import com.bimd.msgsim.service.simulation.broker.SqsBehavior;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BrokerTradeoffServiceTest {

    private final CostEstimator costEstimator =
            new CostEstimator(List.of(new KafkaPricing(), new RabbitMqPricing(), new SqsPricing()));
    private final AnalyticalQueueModel model = new AnalyticalQueueModel(
            List.of(new KafkaBehavior(), new RabbitMqBehavior(), new SqsBehavior()), costEstimator);
    private final BrokerTradeoffService tradeoffService = new BrokerTradeoffService(model);

    @Test
    void should_haveNoIdleConsumers_when_partitionsMatchConsumers() {
        Scenario scenario = scenario(BrokerType.KAFKA, 100, 20, 50);
        scenario.setPartitions(20);

        TradeoffResult result = tradeoffService.evaluate(scenario);

        assertThat(result.find(BrokerType.KAFKA).model().idleConsumers()).isZero();
    }

    @Test
    void should_changeBestBroker_when_consumersExceedPartitions() {
        // only 2 of 20 consumers are ever active on Kafka here, capping its capacity
        // far below what RabbitMQ/SQS reach with the same 20 consumers
        Scenario scenario = scenario(BrokerType.KAFKA, 100, 20, 50);
        scenario.setPartitions(2);

        TradeoffResult result = tradeoffService.evaluate(scenario);

        assertThat(result.best().model().broker()).isNotEqualTo(BrokerType.KAFKA);
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
