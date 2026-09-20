package com.bimd.msgsim.service.report;

import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.service.simulation.Disturbance;
import com.bimd.msgsim.service.simulation.ScenarioVariants;

/**
 * The columns of the broker x scenario matrix. A broker that ties in the healthy case can still
 * diverge when something breaks — which is when retention, redelivery and rebalancing matter.
 */
public enum ScenarioVariant {
    HEALTHY("Saudável"),
    CONSUMER_DOWN("Metade dos consumidores cai"),
    SPIKE_2X("Pico de 2× na produção"),
    FAILURE_10("Falha de 10%");

    private final String label;

    ScenarioVariant(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** The scenario to run for this variant; every variant starts from the base without its own burst. */
    public Scenario apply(Scenario base) {
        Scenario clean = ScenarioVariants.withoutBurst(base);
        return switch (this) {
            case FAILURE_10 -> ScenarioVariants.withFailurePct(clean, Math.max(10, base.getFailurePct().doubleValue()));
            default -> clean;
        };
    }

    public Disturbance disturbance(Scenario base) {
        return switch (this) {
            case CONSUMER_DOWN -> new Disturbance(Math.max(1, base.getConsumers() / 2), 1.0);
            case SPIKE_2X -> new Disturbance(0, 2.0);
            default -> Disturbance.NONE;
        };
    }
}
