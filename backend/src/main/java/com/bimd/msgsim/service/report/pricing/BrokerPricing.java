package com.bimd.msgsim.service.report.pricing;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;

/**
 * Illustrative cost model per broker — orders of magnitude to compare, not a real bill.
 * One implementation per broker keeps the pricing shape (per-request vs per-broker-hour)
 * from leaking into {@code CostEstimator} (Open/Closed, same pattern as {@code BrokerBehavior}).
 */
public interface BrokerPricing {

    BrokerType type();

    /** @param producedTotal produced + ok + retries + dlq, in whatever combination the broker bills by */
    double estimate(Scenario scenario, long producedTotal, long okTotal, long retriesTotal, long dlqTotal, int durationSeconds);
}
