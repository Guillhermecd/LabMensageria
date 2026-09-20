package com.bimd.msgsim.service.simulation;

/**
 * A scripted disturbance layered on a scenario run, so the same scenario can be tested "when
 * something breaks" without new persisted fields. The outage removes {@code consumersLost}
 * consumers for the middle of the run; the spike multiplies production over a burst window.
 */
public record Disturbance(int consumersLost, double spikeMultiplier) {

    public static final Disturbance NONE = new Disturbance(0, 1.0);
    public static final double OUTAGE_START = 0.40;
    public static final double OUTAGE_END = 0.60;

    public boolean outageAt(int second, int duration) {
        return consumersLost > 0 && second >= (int) (duration * OUTAGE_START) && second < (int) (duration * OUTAGE_END);
    }

    public boolean spikeAt(int second, int duration) {
        return spikeMultiplier > 1 && second > duration * 0.42 && second < duration * 0.57;
    }
}
