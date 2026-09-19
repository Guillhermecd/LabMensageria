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

class AnalyticalQueueModelTest {

    private final CostEstimator costEstimator =
            new CostEstimator(List.of(new KafkaPricing(), new RabbitMqPricing(), new SqsPricing()));
    private final AnalyticalQueueModel model = new AnalyticalQueueModel(
            List.of(new KafkaBehavior(), new RabbitMqBehavior(), new SqsBehavior()), costEstimator);

    @Test
    void should_beStable_when_capacityExceedsLoad() {
        Scenario scenario = scenario(BrokerType.KAFKA, 100, 4, 15, 0, 3, 120, true);
        scenario.setPartitions(6);

        BrokerModel result = model.compute(scenario, BrokerType.KAFKA);

        assertThat(result.stable()).isTrue();
        assertThat(result.ratio()).isLessThan(1);
    }

    @Test
    void should_beUnstable_when_loadExceedsCapacity() {
        Scenario scenario = scenario(BrokerType.KAFKA, 10000, 1, 100, 0, 3, 120, true);
        scenario.setPartitions(1);

        BrokerModel result = model.compute(scenario, BrokerType.KAFKA);

        assertThat(result.stable()).isFalse();
        assertThat(result.ratio()).isGreaterThan(1);
    }

    private Scenario scenario(
            BrokerType broker, int rate, int consumers, int processingMs, int failurePct,
            int maxRetries, int durationSeconds, boolean dlqEnabled) {
        Scenario scenario = new Scenario();
        scenario.setName("test");
        scenario.setBroker(broker);
        scenario.setRatePerSecond(rate);
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
