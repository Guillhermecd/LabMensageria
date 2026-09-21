package com.bimd.msgsim.service.report;

import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.service.simulation.Disturbance;
import com.bimd.msgsim.service.simulation.ScenarioVariants;

/**
 * The columns of the broker x scenario matrix. Each variant declares a target occupation, not an
 * absolute rate: the rate is {@code occupation x capacity}, so "healthy" stays healthy whatever the
 * form says. A broker that ties in the healthy case can still diverge when something breaks —
 * which is when retention, redelivery and rebalancing matter.
 */
public enum ScenarioVariant {
    HEALTHY("Saudável", 70),
    CONSUMER_DOWN("Metade dos consumidores cai", 70),
    SPIKE_2X("Pico de 2× na produção", 70),
    FAILURE_10("Falha de 10%", 70),
    OVERLOAD_5X("Sobrecarga 5×", 500);

    private final String label;
    private final int occupancyPct;

    ScenarioVariant(String label, int occupancyPct) {
        this.label = label;
        this.occupancyPct = occupancyPct;
    }

    public String label() {
        return label;
    }

    public int occupancyPct() {
        return occupancyPct;
    }

    /** Messages per second this variant offers: target occupation x the reference capacity. */
    public int ratePerSecond(double capacityPerSecond) {
        return (int) Math.max(1, Math.round(capacityPerSecond * occupancyPct / 100.0));
    }

    /**
     * The scenario to run for this variant. It starts from the base without its own burst and
     * replaces the rate; {@code capacityPerSecond} is the reference capacity (parallelism / service time)
     * of the broker the base scenario was configured for, so all three brokers get the same load.
     */
    public Scenario apply(Scenario base, double capacityPerSecond) {
        Scenario loaded = ScenarioVariants.withRate(ScenarioVariants.withoutBurst(base), ratePerSecond(capacityPerSecond));
        return switch (this) {
            case FAILURE_10 -> ScenarioVariants.withFailurePct(loaded, Math.max(10, base.getFailurePct().doubleValue()));
            default -> loaded;
        };
    }

    public Disturbance disturbance(Scenario base) {
        return switch (this) {
            case CONSUMER_DOWN -> new Disturbance(Math.max(1, base.getConsumers() / 2), 1.0);
            case SPIKE_2X -> new Disturbance(0, 2.0);
            default -> Disturbance.NONE;
        };
    }

    /** What the variant does, for the UI: the load and the disturbance window. */
    public String detail(Scenario base) {
        int duration = base.getDurationSeconds();
        return switch (this) {
            case CONSUMER_DOWN -> "metade dos consumidores sai aos " + (int) (duration * Disturbance.OUTAGE_START)
                    + " s e volta aos " + (int) (duration * Disturbance.OUTAGE_END) + " s";
            case SPIKE_2X -> "produção ×2 por " + Disturbance.spikeSeconds(duration) + " s, a partir de "
                    + Disturbance.spikeStartSecond(duration) + " s";
            case FAILURE_10 -> "falha de " + Math.max(10, Math.round(base.getFailurePct().doubleValue())) + "%";
            case OVERLOAD_5X -> "produção 5× acima da capacidade durante toda a corrida";
            default -> "sem perturbação";
        };
    }
}
