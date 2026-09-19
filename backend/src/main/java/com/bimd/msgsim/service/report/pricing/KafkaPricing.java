package com.bimd.msgsim.service.report.pricing;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import org.springframework.stereotype.Component;

/** Billed by broker-hour (one broker per 6 partitions) plus data transferred. */
@Component
public class KafkaPricing implements BrokerPricing {

    private static final int PARTITIONS_PER_BROKER = 6;
    private static final double COST_PER_BROKER_HOUR = 0.11;
    private static final double COST_PER_GB = 0.10;

    @Override
    public BrokerType type() {
        return BrokerType.KAFKA;
    }

    @Override
    public double estimate(
            Scenario scenario, long producedTotal, long okTotal, long retriesTotal, long dlqTotal, int durationSeconds) {
        int partitions = scenario.getPartitions() != null ? scenario.getPartitions() : 1;
        int brokers = Math.max(1, (int) Math.ceil(partitions / (double) PARTITIONS_PER_BROKER));
        double gb = producedTotal * scenario.getMessageSizeKb() / 1e6;
        double hours = Math.max(1, durationSeconds) / 3600.0;
        return hours * brokers * COST_PER_BROKER_HOUR + gb * COST_PER_GB;
    }
}
