package com.bimd.msgsim.service.simulation.broker;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.EventType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.service.simulation.EventResult;
import com.bimd.msgsim.service.simulation.SimulationState;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ semantics: above the memory high watermark the broker blocks publishers, so the backlog
 * stabilises instead of growing and nothing is dropped; every consumer can read the queue, with
 * prefetch bounding how many unacknowledged messages each holds; a nack requeues at once. There is
 * no TTL by default — only an explicit {@code max-length} (drop-head) removes messages.
 */
@Component
public class RabbitMqBehavior implements BrokerBehavior {

    static final int DEFAULT_HIGH_WATERMARK_MB = 256;
    static final int DEFAULT_PREFETCH = 250;
    /** Round trip a consumer waits for the next delivery once its prefetch window is empty. */
    static final double DELIVERY_ROUND_TRIP_MS = 2.0;

    @Override
    public BrokerType type() {
        return BrokerType.RABBITMQ;
    }

    @Override
    public Admission admit(SimulationState state, Scenario scenario) {
        int watermarkMb = scenario.getHighWatermarkMb() != null
                ? scenario.getHighWatermarkMb()
                : DEFAULT_HIGH_WATERMARK_MB;
        return state.getQueue() >= BrokerBehavior.messagesThatFit(scenario, watermarkMb)
                ? Admission.BLOCK_PRODUCER
                : Admission.ACCEPT;
    }

    @Override
    public int parallelism(SimulationState state, Scenario scenario) {
        return effectiveConsumers(scenario);
    }

    /**
     * Every consumer works, but with a small prefetch it idles a delivery round trip per
     * {@code prefetch} messages: efficiency = service / (service + roundTrip / prefetch).
     */
    @Override
    public int effectiveConsumers(Scenario scenario) {
        int prefetch = scenario.getPrefetch() != null ? scenario.getPrefetch() : DEFAULT_PREFETCH;
        double service = Math.max(1, scenario.getProcessingMs());
        double efficiency = service / (service + DELIVERY_ROUND_TRIP_MS / Math.max(1, prefetch));
        return Math.max(1, (int) Math.round(scenario.getConsumers() * efficiency));
    }

    @Override
    public long retryDelayMs(SimulationState state, Scenario scenario, int failures) {
        return 0;
    }

    @Override
    public int sweep(SimulationState state, Scenario scenario, int second) {
        Integer capacity = scenario.getQueueCapacity();
        if (capacity == null || capacity <= 0 || state.getQueue() <= capacity) {
            return 0;
        }
        int dropped = state.dropOldest((int) Math.round(state.getQueue() - capacity));
        if (!state.isDropSeen()) {
            state.setDropSeen(true);
            state.addEvent(new EventResult(
                    second,
                    EventType.QUEUE_FULL,
                    "Fila atingiu a capacidade (" + capacity + "). Mensagens começaram a ser descartadas (overflow)."));
        }
        return dropped;
    }

    @Override
    public java.util.OptionalLong backlogCeiling(Scenario scenario) {
        int watermarkMb = scenario.getHighWatermarkMb() != null
                ? scenario.getHighWatermarkMb()
                : DEFAULT_HIGH_WATERMARK_MB;
        long ceiling = BrokerBehavior.messagesThatFit(scenario, watermarkMb);
        Integer maxLength = scenario.getQueueCapacity();
        return java.util.OptionalLong.of(maxLength != null && maxLength > 0 ? Math.min(ceiling, maxLength) : ceiling);
    }

    @Override
    public int latencyOverheadMs() {
        return 2;
    }

    @Override
    public String firstDlqMessage() {
        return "Primeiras mensagens movidas para a dead-letter queue.";
    }

    @Override
    public int analyticalRetryDelayMs(Scenario scenario) {
        return scenario.getProcessingMs();
    }

    @Override
    public double operationalSimplicityScore() {
        return 0.70;
    }
}
