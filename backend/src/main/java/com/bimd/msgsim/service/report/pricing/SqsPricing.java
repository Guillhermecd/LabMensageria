package com.bimd.msgsim.service.report.pricing;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import org.springframework.stereotype.Component;

/** Billed per request (publish + every receive attempt, including retries and DLQ moves) plus data transferred. */
@Component
public class SqsPricing implements BrokerPricing {

    private static final double COST_PER_MILLION_REQUESTS = 0.40;
    private static final double COST_PER_GB = 0.09;

    @Override
    public BrokerType type() {
        return BrokerType.SQS;
    }

    @Override
    public double estimate(
            Scenario scenario, long producedTotal, long okTotal, long retriesTotal, long dlqTotal, int durationSeconds) {
        long requests = producedTotal + okTotal + retriesTotal + dlqTotal;
        double gb = producedTotal * scenario.getMessageSizeKb() / 1e6;
        return requests / 1e6 * COST_PER_MILLION_REQUESTS + gb * COST_PER_GB;
    }
}
