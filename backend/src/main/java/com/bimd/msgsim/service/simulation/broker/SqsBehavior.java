package com.bimd.msgsim.service.simulation.broker;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.service.simulation.SimulationState;
import org.springframework.stereotype.Component;

/**
 * SQS semantics: a failed message only becomes visible again after the visibility
 * timeout elapses, unlike Kafka/RabbitMQ where a retry can happen on the very next tick.
 */
@Component
public class SqsBehavior implements BrokerBehavior {

    private static final int DEFAULT_VISIBILITY_TIMEOUT_SECONDS = 30;

    @Override
    public BrokerType type() {
        return BrokerType.SQS;
    }

    @Override
    public int effectiveConsumers(Scenario scenario) {
        return scenario.getConsumers();
    }

    @Override
    public int applyOverflow(SimulationState state, Scenario scenario, int second) {
        return 0; // SQS has no practical queue limit
    }

    @Override
    public int retryDelaySeconds(Scenario scenario) {
        int visibilityTimeout = scenario.getVisibilityTimeoutSeconds() != null
                ? scenario.getVisibilityTimeoutSeconds()
                : DEFAULT_VISIBILITY_TIMEOUT_SECONDS;
        return Math.min(visibilityTimeout, scenario.getDurationSeconds());
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
        int visibilityTimeout = scenario.getVisibilityTimeoutSeconds() != null
                ? scenario.getVisibilityTimeoutSeconds()
                : DEFAULT_VISIBILITY_TIMEOUT_SECONDS;
        return visibilityTimeout * 1000;
    }

    @Override
    public double operationalSimplicityScore() {
        return 1.0; // fully managed, nothing to operate
    }
}
