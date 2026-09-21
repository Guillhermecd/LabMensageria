package com.bimd.msgsim.service.simulation.broker;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.EventType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.service.simulation.EventResult;
import com.bimd.msgsim.service.simulation.SimulationState;
import org.springframework.stereotype.Component;

/**
 * SQS semantics: the queue accepts everything, a failed message only becomes visible again after
 * the visibility timeout, messages received but not deleted (being processed or hidden) count
 * against the in-flight ceiling, and messages older than the retention period disappear.
 */
@Component
public class SqsBehavior implements BrokerBehavior {

    static final int DEFAULT_VISIBILITY_TIMEOUT_SECONDS = 30;
    static final int DEFAULT_INFLIGHT_MAX = 120_000;
    static final double RETENTION_MS = 4 * 24 * 3_600_000.0;

    @Override
    public BrokerType type() {
        return BrokerType.SQS;
    }

    @Override
    public Admission admit(SimulationState state, Scenario scenario) {
        return Admission.ACCEPT;
    }

    @Override
    public int parallelism(SimulationState state, Scenario scenario) {
        int free = Math.max(0, inflightMax(scenario) - state.getHiddenRetries());
        return Math.min(scenario.getConsumers(), free);
    }

    @Override
    public int effectiveConsumers(Scenario scenario) {
        return Math.min(scenario.getConsumers(), inflightMax(scenario));
    }

    @Override
    public long retryDelayMs(SimulationState state, Scenario scenario, int failures) {
        return visibilityTimeoutSeconds(scenario) * 1000L;
    }

    @Override
    public int sweep(SimulationState state, Scenario scenario, int second) {
        int dropped = state.dropOlderThan(RETENTION_MS);
        if (dropped > 0 && !state.isDropSeen()) {
            state.setDropSeen(true);
            state.addEvent(new EventResult(second, EventType.QUEUE_FULL,
                    "Mensagens passaram do período de retenção (4 dias) e foram apagadas."));
        }
        return dropped;
    }

    @Override
    public java.util.OptionalLong backlogCeiling(Scenario scenario) {
        return java.util.OptionalLong.empty(); // 4-day retention is not reachable in a simulation window
    }

    @Override
    public int latencyOverheadMs() {
        return 20;
    }

    @Override
    public String firstDlqMessage() {
        return "Primeiras mensagens movidas para a dead-letter queue.";
    }

    @Override
    public int analyticalRetryDelayMs(Scenario scenario) {
        return visibilityTimeoutSeconds(scenario) * 1000;
    }

    @Override
    public double operationalSimplicityScore() {
        return 1.0; // fully managed, nothing to operate
    }

    private static int visibilityTimeoutSeconds(Scenario scenario) {
        return scenario.getVisibilityTimeoutSeconds() != null
                ? scenario.getVisibilityTimeoutSeconds()
                : DEFAULT_VISIBILITY_TIMEOUT_SECONDS;
    }

    private static int inflightMax(Scenario scenario) {
        return scenario.getInflightMax() != null ? scenario.getInflightMax() : DEFAULT_INFLIGHT_MAX;
    }
}
