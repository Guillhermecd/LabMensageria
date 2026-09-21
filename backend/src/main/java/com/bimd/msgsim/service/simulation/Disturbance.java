package com.bimd.msgsim.service.simulation;

/**
 * A scripted disturbance layered on a scenario run, so the same scenario can be tested "when
 * something breaks" without new persisted fields. The outage removes {@code consumersLost}
 * consumers from the middle of the run for a quarter of it; the spike multiplies production for an
 * explicit window (not the whole run).
 */
public record Disturbance(int consumersLost, double spikeMultiplier) {

    public static final Disturbance NONE = new Disturbance(0, 1.0);
    /** The outage starts at the middle of the run and lasts a quarter of it. */
    public static final double OUTAGE_START = 0.50;
    public static final double OUTAGE_END = 0.75;
    public static final double SPIKE_START = 0.50;
    /** Share of the run the spike lasts. */
    public static final double SPIKE_LENGTH = 0.15;

    public boolean outageAt(int second, int duration) {
        return consumersLost > 0 && second >= (int) (duration * OUTAGE_START) && second < (int) (duration * OUTAGE_END);
    }

    public boolean spikeAt(int second, int duration) {
        int start = spikeStartSecond(duration);
        return spikeMultiplier > 1 && second >= start && second < start + spikeSeconds(duration);
    }

    public static int spikeStartSecond(int duration) {
        return (int) (duration * SPIKE_START);
    }

    /** How long the spike lasts, in seconds (at least one). */
    public static int spikeSeconds(int duration) {
        return Math.max(1, (int) Math.round(duration * SPIKE_LENGTH));
    }
}
