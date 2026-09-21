package com.bimd.msgsim.service.simulation.broker;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.EventType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.service.simulation.EventResult;
import com.bimd.msgsim.service.simulation.SimulationState;
import org.springframework.stereotype.Component;

/**
 * Kafka semantics: the log accepts everything (append), partitions cap parallelism (a partition is
 * read by one consumer of the group), a failed record is retried at once because the consumer does
 * not advance its offset, and retention deletes the oldest records once the backlog no longer fits.
 */
@Component
public class KafkaBehavior implements BrokerBehavior {

    static final int DEFAULT_PARTITIONS = 6;
    static final int DEFAULT_RETENTION_HOURS = 168;
    /** Kept small on purpose: a 120 s run only ever holds a few hundred MB of backlog. */
    static final int DEFAULT_RETENTION_MB = 256;

    @Override
    public BrokerType type() {
        return BrokerType.KAFKA;
    }

    @Override
    public Admission admit(SimulationState state, Scenario scenario) {
        return Admission.ACCEPT;
    }

    @Override
    public int parallelism(SimulationState state, Scenario scenario) {
        return effectiveConsumers(scenario);
    }

    @Override
    public int effectiveConsumers(Scenario scenario) {
        int partitions = scenario.getPartitions() != null ? scenario.getPartitions() : DEFAULT_PARTITIONS;
        return Math.min(scenario.getConsumers(), partitions);
    }

    @Override
    public long retryDelayMs(SimulationState state, Scenario scenario, int failures) {
        return 0;
    }

    @Override
    public int sweep(SimulationState state, Scenario scenario, int second) {
        int retentionMb = scenario.getRetentionMb() != null ? scenario.getRetentionMb() : DEFAULT_RETENTION_MB;
        int retentionHours = scenario.getRetentionHours() != null
                ? scenario.getRetentionHours()
                : DEFAULT_RETENTION_HOURS;
        long fits = BrokerBehavior.messagesThatFit(scenario, retentionMb);
        int dropped = 0;
        if (state.getQueue() > fits) {
            dropped += state.dropOldest((int) (state.getQueue() - fits));
        }
        dropped += state.dropOlderThan(retentionHours * 3_600_000.0);
        if (dropped > 0 && !state.isDropSeen()) {
            state.setDropSeen(true);
            state.addEvent(new EventResult(second, EventType.QUEUE_FULL,
                    "Retenção do log excedida (" + retentionMb + " MB / " + retentionHours
                            + " h): os registros mais antigos foram apagados antes de serem consumidos."));
        }
        return dropped;
    }

    @Override
    public java.util.OptionalLong backlogCeiling(Scenario scenario) {
        int retentionMb = scenario.getRetentionMb() != null ? scenario.getRetentionMb() : DEFAULT_RETENTION_MB;
        return java.util.OptionalLong.of(BrokerBehavior.messagesThatFit(scenario, retentionMb));
    }

    @Override
    public int failoverPauseSeconds() {
        return 6;
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
