package com.bimd.msgsim.service.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mutable per-run state that {@link SimulationEngine} advances one second at a time.
 * Not persisted directly — {@code SimulationRunService} maps {@link #ticks} and
 * {@link #events} to JPA entities once the run finishes.
 */
public class SimulationState {

    final Random random;
    final Disturbance disturbance;
    final List<TickResult> ticks = new ArrayList<>();
    final List<EventResult> events = new ArrayList<>();
    final List<RetryBucket> retryBuckets = new ArrayList<>();

    double queue;
    long produced;
    long ok;
    long dlq;
    long dropped;
    long retries;
    int satStreak;
    boolean saturated;
    boolean dlqSeen;
    boolean dropSeen;
    boolean lagWarn;
    boolean burstSeen;
    boolean burstEnd;
    boolean done;
    int t;
    /** Consecutive seconds with zero consumption capacity: messages queued in that span all wait it out. */
    int stallSeconds;

    public SimulationState(long seed) {
        this(seed, Disturbance.NONE);
    }

    public SimulationState(long seed, Disturbance disturbance) {
        this.disturbance = disturbance;
        this.random = new Random(seed);
    }

    public double getQueue() {
        return queue;
    }

    public void setQueue(double queue) {
        this.queue = queue;
    }

    public void addDropped(int amount) {
        this.dropped += amount;
    }

    public boolean isDropSeen() {
        return dropSeen;
    }

    public void setDropSeen(boolean dropSeen) {
        this.dropSeen = dropSeen;
    }

    public void addEvent(EventResult event) {
        this.events.add(event);
    }

    public List<TickResult> getTicks() {
        return ticks;
    }

    public List<EventResult> getEvents() {
        return events;
    }

    public boolean isDone() {
        return done;
    }

    public long getProduced() {
        return produced;
    }

    public long getOk() {
        return ok;
    }

    public long getDlq() {
        return dlq;
    }

    public long getDropped() {
        return dropped;
    }

    public long getRetries() {
        return retries;
    }
}
