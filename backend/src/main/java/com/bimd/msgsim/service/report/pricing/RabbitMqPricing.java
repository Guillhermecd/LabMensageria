package com.bimd.msgsim.service.report.pricing;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import org.springframework.stereotype.Component;

/** Billed by node-hour (one node per 4 consumers) plus data transferred. */
@Component
public class RabbitMqPricing implements BrokerPricing {

    private static final int CONSUMERS_PER_NODE = 4;
    private static final double COST_PER_NODE_HOUR = 0.09;
    private static final double COST_PER_GB = 0.05;

    @Override
    public BrokerType type() {
        return BrokerType.RABBITMQ;
    }

    @Override
    public double estimate(
            Scenario scenario, long producedTotal, long okTotal, long retriesTotal, long dlqTotal, int durationSeconds) {
        double gb = producedTotal * scenario.getMessageSizeKb() / 1e6;
        double hours = Math.max(1, durationSeconds) / 3600.0;
        int nodes = (int) Math.ceil(scenario.getConsumers() / (double) CONSUMERS_PER_NODE);
        return hours * COST_PER_NODE_HOUR * nodes + gb * COST_PER_GB;
    }
}
