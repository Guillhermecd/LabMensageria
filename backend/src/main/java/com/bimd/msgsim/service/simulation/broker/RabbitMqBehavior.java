package com.bimd.msgsim.service.simulation.broker;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.EventType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.service.simulation.EventResult;
import com.bimd.msgsim.service.simulation.SimulationState;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ semantics: a queue with {@code max-length} drops the oldest messages once
 * full (overflow policy {@code drop-head}). Every other broker keeps this class
 * untouched — that's the point of keeping one class per broker (Open/Closed).
 */
@Component
public class RabbitMqBehavior implements BrokerBehavior {

    @Override
    public BrokerType type() {
        return BrokerType.RABBITMQ;
    }

    @Override
    public int effectiveConsumers(Scenario scenario) {
        return scenario.getConsumers();
    }

    @Override
    public int applyOverflow(SimulationState state, Scenario scenario, int second) {
        Integer capacity = scenario.getQueueCapacity();
        if (capacity == null || capacity <= 0 || state.getQueue() <= capacity) {
            return 0;
        }
        int dropped = (int) Math.round(state.getQueue() - capacity);
        state.setQueue(capacity);
        state.addDropped(dropped);
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
    public int retryDelaySeconds(Scenario scenario) {
        return 0;
    }

    @Override
    public int latencyOverheadMs() {
        return 2;
    }

    @Override
    public String firstDlqMessage() {
        return "Primeiras mensagens movidas para a dead-letter queue.";
    }
}
