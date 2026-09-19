package com.bimd.msgsim.service.simulation.broker;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.service.simulation.SimulationState;
import org.springframework.stereotype.Component;

/**
 * Kafka semantics: partitions cap parallelism. A consumer group can never have more
 * active consumers than partitions — extra consumers sit idle (Open/Closed: adding
 * this broker required no change to {@code SimulationEngine}).
 */
@Component
public class KafkaBehavior implements BrokerBehavior {

    @Override
    public BrokerType type() {
        return BrokerType.KAFKA;
    }

    @Override
    public int effectiveConsumers(Scenario scenario) {
        int partitions = scenario.getPartitions() != null ? scenario.getPartitions() : 1;
        return Math.min(scenario.getConsumers(), partitions);
    }

    @Override
    public int applyOverflow(SimulationState state, Scenario scenario, int second) {
        return 0; // log-based retention: Kafka never drops messages for a full queue
    }

    @Override
    public int retryDelaySeconds(Scenario scenario) {
        return 0;
    }

    @Override
    public int latencyOverheadMs() {
        return 5;
    }

    @Override
    public String firstDlqMessage() {
        return "Primeiras mensagens enviadas ao tópico de DLQ após esgotar retries.";
    }

    @Override
    public int analyticalRetryDelayMs(Scenario scenario) {
        return scenario.getProcessingMs();
    }

    @Override
    public double operationalSimplicityScore() {
        return 0.45; // brokers, partitions and offsets to operate
    }
}
