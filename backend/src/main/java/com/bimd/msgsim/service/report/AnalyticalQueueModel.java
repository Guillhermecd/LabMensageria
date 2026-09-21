package com.bimd.msgsim.service.report;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.domain.model.ServiceProfile;
import com.bimd.msgsim.service.simulation.broker.BrokerBehavior;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Simplified M/M/c queueing estimate: given a scenario's load, what would each broker's
 * capacity, latency, backlog and loss look like — without running a single tick. Reuses
 * {@link BrokerBehavior} for the same broker-specific numbers the simulation engine uses
 * (effective consumers, latency overhead), so the two never drift apart by accident.
 */
@Component
public class AnalyticalQueueModel {

    private final Map<BrokerType, BrokerBehavior> behaviors;
    private final CostEstimator costEstimator;

    public AnalyticalQueueModel(List<BrokerBehavior> implementations, CostEstimator costEstimator) {
        this.behaviors = new EnumMap<>(BrokerType.class);
        for (BrokerBehavior behavior : implementations) {
            behaviors.put(behavior.type(), behavior);
        }
        this.costEstimator = costEstimator;
    }

    /** Backlog (messages) at which the broker starts dropping or blocking, if it has such a ceiling. */
    public java.util.OptionalLong backlogCeiling(Scenario scenario, BrokerType broker) {
        return behaviors.get(broker).backlogCeiling(scenario);
    }

    public BrokerModel compute(Scenario scenario, BrokerType broker) {
        BrokerBehavior behavior = behaviors.get(broker);
        int rate = scenario.getRatePerSecond();
        int processingMs = scenario.getProcessingMs();
        int duration = scenario.getDurationSeconds();
        double failureRate = scenario.getFailurePct().doubleValue() / 100.0;

        int effectiveConsumers = behavior.effectiveConsumers(scenario);
        double capacity = effectiveConsumers * (1000.0 / Math.max(1, processingMs));
        double ratio = rate / capacity;
        boolean stable = ratio < 1;
        ServiceProfile profile = scenario.getServiceProfile();
        double wait = stable
                ? profile.queueingWaitMs(ratio, effectiveConsumers, processingMs)
                : duration * 1000.0 * (1 - 1 / ratio) / 2;

        int overhead = behavior.latencyOverheadMs();
        int retryDelayMs = behavior.analyticalRetryDelayMs(scenario);
        double p50 = processingMs + overhead + wait;
        double p99 = processingMs * profile.p99Factor() + overhead + wait * 1.4
                + failureRate * (retryDelayMs + processingMs * 10) * 3;

        long produced = (long) rate * duration;
        double burstExtra = scenario.isBurstEnabled() ? rate * 2.0 * duration * 0.15 : 0;
        double backlogEnd = Math.max(0, (rate - capacity) * duration)
                + (scenario.isBurstEnabled()
                        ? Math.max(0, rate * 3 - capacity) * duration * 0.15 - Math.max(0, capacity - rate) * duration * 0.43
                        : 0);
        long backlog = Math.max(0, Math.round(backlogEnd));

        double dropped = 0;
        Integer queueCapacity = scenario.getQueueCapacity();
        if (broker == BrokerType.RABBITMQ && queueCapacity != null && queueCapacity > 0) {
            dropped = Math.max(0, backlog - queueCapacity)
                    + (scenario.isBurstEnabled() && rate * 3 > capacity
                            ? Math.max(0, (rate * 3 - capacity) * duration * 0.15 - queueCapacity)
                            : 0);
        }
        long dlq = scenario.isDlqEnabled()
                ? Math.round((produced + burstExtra) * Math.pow(Math.max(failureRate, 0.0001), scenario.getMaxRetries() + 1))
                : 0;
        long loss = Math.round(dropped + dlq);
        double lossPct = loss / (produced + burstExtra) * 100;
        long retries = Math.round(produced * failureRate);

        double cost = costEstimator.estimate(
                scenario, broker, Math.round(produced + burstExtra), produced, retries, dlq, duration);
        double idleConsumers = broker == BrokerType.KAFKA ? scenario.getConsumers() - effectiveConsumers : 0;

        return new BrokerModel(
                broker, effectiveConsumers, capacity, ratio, stable, p50, p99, backlog, loss, lossPct, cost,
                behavior.operationalSimplicityScore(), idleConsumers);
    }
}
