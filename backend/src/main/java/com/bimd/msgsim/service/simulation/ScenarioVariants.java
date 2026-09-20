package com.bimd.msgsim.service.simulation;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import java.math.BigDecimal;

/**
 * Transient scenario copies for what-if runs. The engine and the analytical model only read fields,
 * so nothing derived here is ever persisted.
 */
public final class ScenarioVariants {

    private ScenarioVariants() {
    }

    public static Scenario copy(Scenario source) {
        Scenario copy = new Scenario();
        copy.setName(source.getName());
        copy.setBroker(source.getBroker());
        copy.setRatePerSecond(source.getRatePerSecond());
        copy.setConsumers(source.getConsumers());
        copy.setProcessingMs(source.getProcessingMs());
        copy.setFailurePct(source.getFailurePct());
        copy.setMaxRetries(source.getMaxRetries());
        copy.setMessageSizeKb(source.getMessageSizeKb());
        copy.setDurationSeconds(source.getDurationSeconds());
        copy.setQueueCapacity(source.getQueueCapacity());
        copy.setPartitions(source.getPartitions());
        copy.setVisibilityTimeoutSeconds(source.getVisibilityTimeoutSeconds());
        copy.setDlqEnabled(source.isDlqEnabled());
        copy.setBurstEnabled(source.isBurstEnabled());
        copy.setExecutionMode(source.getExecutionMode());
        copy.setServiceProfile(source.getServiceProfile());
        return copy;
    }

    public static Scenario withBroker(Scenario source, BrokerType broker) {
        Scenario copy = copy(source);
        copy.setBroker(broker);
        return copy;
    }

    public static Scenario withRate(Scenario source, int ratePerSecond) {
        Scenario copy = copy(source);
        copy.setRatePerSecond(Math.max(1, ratePerSecond));
        return copy;
    }

    public static Scenario withFailurePct(Scenario source, double failurePct) {
        Scenario copy = copy(source);
        copy.setFailurePct(BigDecimal.valueOf(failurePct));
        return copy;
    }

    public static Scenario withoutBurst(Scenario source) {
        Scenario copy = copy(source);
        copy.setBurstEnabled(false);
        return copy;
    }
}
