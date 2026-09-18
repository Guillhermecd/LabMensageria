package com.bimd.msgsim.service.report;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.service.report.pricing.BrokerPricing;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CostEstimator {

    private final Map<BrokerType, BrokerPricing> pricings;

    public CostEstimator(List<BrokerPricing> implementations) {
        this.pricings = new EnumMap<>(BrokerType.class);
        for (BrokerPricing pricing : implementations) {
            pricings.put(pricing.type(), pricing);
        }
    }

    public double estimate(
            Scenario scenario,
            BrokerType broker,
            long producedTotal,
            long okTotal,
            long retriesTotal,
            long dlqTotal,
            int durationSeconds) {
        return pricings.get(broker).estimate(scenario, producedTotal, okTotal, retriesTotal, dlqTotal, durationSeconds);
    }
}
