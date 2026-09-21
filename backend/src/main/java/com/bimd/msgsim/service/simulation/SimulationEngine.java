package com.bimd.msgsim.service.simulation;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.EventType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.service.simulation.broker.Admission;
import com.bimd.msgsim.service.simulation.broker.BrokerBehavior;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Discrete-event simulator: a min-heap of future events (arrival, completion, retry reappearance and
 * a per-second sweep) ordered by instant; the clock jumps to the next event. Every message is tracked
 * individually, so latencies are measured, not estimated. Broker-specific rules are delegated to
 * {@link BrokerBehavior} so this class never changes when a broker is added (Open/Closed).
 *
 * <p>{@link #step} advances to the next one-second boundary and emits one {@link TickResult}; that
 * is the unit the live stream and the persisted series use. Two invariants run on every simulation:
 * message conservation (fails loudly) and Little's law (warns above 2%).
 */
@Component
public class SimulationEngine {

    private static final Logger log = LoggerFactory.getLogger(SimulationEngine.class);
    private static final double LITTLE_TOLERANCE = 0.02;

    private final Map<BrokerType, BrokerBehavior> behaviors;

    public SimulationEngine(List<BrokerBehavior> implementations) {
        this.behaviors = new EnumMap<>(BrokerType.class);
        for (BrokerBehavior behavior : implementations) {
            behaviors.put(behavior.type(), behavior);
        }
    }

    public SimulationState newState(long seed) {
        return new SimulationState(seed);
    }

    public SimulationState runToCompletion(Scenario scenario, long seed) {
        return runToCompletion(scenario, seed, Disturbance.NONE);
    }

    public SimulationState runToCompletion(Scenario scenario, long seed, Disturbance disturbance) {
        SimulationState state = new SimulationState(seed, disturbance);
        while (!state.isDone()) {
            step(state, scenario);
        }
        return state;
    }

    /** Processes events up to the next one-second boundary and records that second's tick. */
    public void step(SimulationState sim, Scenario scenario) {
        BrokerBehavior behavior = behaviors.get(scenario.getBroker());
        if (!sim.started) {
            start(sim, scenario, behavior);
        }
        while (!sim.done) {
            SimEvent event = sim.heap.poll();
            advanceClock(sim, event.timeMs);
            switch (event.kind) {
                case ARRIVAL -> onArrival(sim, scenario, behavior);
                case COMPLETION -> onCompletion(sim, scenario, behavior, event);
                case REAPPEAR -> {
                    sim.retryPending--;
                    sim.waiting.add(event.arrivalMs, event.failures);
                    dispatch(sim, scenario, behavior);
                }
                case SWEEP -> {
                    closeSecond(sim, scenario, behavior);
                    return;
                }
            }
        }
    }

    private void start(SimulationState sim, Scenario scenario, BrokerBehavior behavior) {
        sim.started = true;
        sim.consumerCap = consumerCapAt(sim, scenario, behavior, 0);
        sim.servers = currentServers(sim, scenario, behavior);
        schedule(sim, SimEvent.Kind.SWEEP, 1000, 0, 0);
        scheduleNextArrival(sim, scenario, 0);
    }

    private void advanceClock(SimulationState sim, double toMs) {
        double dt = toMs - sim.lastAccumMs;
        sim.busyMsSecond += sim.busy * dt;
        sim.inSystemArea += sim.inSystem * dt;
        sim.lastAccumMs = toMs;
        sim.nowMs = toMs;
    }

    private void onArrival(SimulationState sim, Scenario scenario, BrokerBehavior behavior) {
        switch (behavior.admit(sim, scenario)) {
            case BLOCK_PRODUCER -> {
                // The producer stops publishing: no arrival is scheduled until the backlog falls.
                sim.producerBlocked = true;
                sim.blockedSinceMs = sim.nowMs;
                sim.markCeiling();
                if (!sim.blockSeen) {
                    sim.blockSeen = true;
                    sim.addEvent(new EventResult((int) (sim.nowMs / 1000), EventType.QUEUE_FULL,
                            "Fila acima do limite de memória: o broker bloqueou o produtor (nada é descartado)."));
                }
                return;
            }
            case REJECT -> {
                sim.produced++;
                sim.arrivalsSecond++;
                sim.dropped++;
                sim.droppedSecond++;
            }
            case ACCEPT -> {
                sim.produced++;
                sim.arrivalsSecond++;
                sim.inSystem++;
                sim.waiting.add(sim.nowMs, 0);
            }
        }
        scheduleNextArrival(sim, scenario, sim.nowMs);
        dispatch(sim, scenario, behavior);
    }

    /** A blocked producer resumes as soon as the broker would accept a message again. */
    private void resumeProducerIfPossible(SimulationState sim, Scenario scenario, BrokerBehavior behavior) {
        if (sim.producerBlocked && behavior.admit(sim, scenario) == Admission.ACCEPT) {
            sim.producerBlocked = false;
            sim.blockedMs += sim.nowMs - sim.blockedSinceMs;
            scheduleNextArrival(sim, scenario, sim.nowMs);
        }
    }

    private void onCompletion(SimulationState sim, Scenario scenario, BrokerBehavior behavior, SimEvent event) {
        sim.busy--;
        double failureRate = scenario.getFailurePct().doubleValue() / 100.0;
        boolean failed = sim.failureRandom.nextDouble() < failureRate;
        if (!failed) {
            sim.ok++;
            sim.okSecond++;
            sim.inSystem--;
            double sojourn = sim.nowMs - event.arrivalMs;
            sim.sojournSum += sojourn;
            sim.recordLatency(sojourn + behavior.latencyOverheadMs());
        } else {
            sim.failedSecond++;
            int failures = event.failures + 1;
            if (failures > scenario.getMaxRetries()) {
                // Retries exhausted: dead-letter it, or discard it when there is no DLQ.
                sim.inSystem--;
                sim.sojournSum += sim.nowMs - event.arrivalMs;
                if (scenario.isDlqEnabled()) {
                    sim.dlq++;
                    if (!sim.dlqSeen) {
                        sim.dlqSeen = true;
                        sim.addEvent(new EventResult(
                                (int) (sim.nowMs / 1000), EventType.FIRST_DLQ, behavior.firstDlqMessage()));
                    }
                } else {
                    sim.dropped++;
                    sim.droppedSecond++;
                }
            } else {
                sim.retries++;
                long delayMs = behavior.retryDelayMs(sim, scenario, failures);
                if (delayMs > 0) {
                    sim.retryPending++;
                    schedule(sim, SimEvent.Kind.REAPPEAR, sim.nowMs + delayMs, event.arrivalMs, failures);
                } else {
                    sim.waiting.add(event.arrivalMs, failures);
                }
            }
        }
        resumeProducerIfPossible(sim, scenario, behavior);
        dispatch(sim, scenario, behavior);
    }

    private int currentServers(SimulationState sim, Scenario scenario, BrokerBehavior behavior) {
        return Math.max(0, Math.min(behavior.parallelism(sim, scenario), sim.consumerCap));
    }

    /** Starts service for waiting messages while a consumer is free. */
    private void dispatch(SimulationState sim, Scenario scenario, BrokerBehavior behavior) {
        sim.servers = currentServers(sim, scenario, behavior);
        if (behavior.parallelism(sim, scenario) < behavior.effectiveConsumers(scenario)) {
            sim.throttledSeen = true;
        }
        while (sim.busy < sim.servers && sim.waiting.size() > 0) {
            double arrival = sim.waiting.poll();
            int failures = sim.waiting.lastFailures();
            sim.busy++;
            double serviceMs = scenario.getServiceProfile().sampleServiceMs(
                    scenario.getProcessingMs(), sim.serviceRandom.nextDouble());
            schedule(sim, SimEvent.Kind.COMPLETION, sim.nowMs + serviceMs, arrival, failures);
        }
    }

    private void schedule(SimulationState sim, SimEvent.Kind kind, double timeMs, double arrivalMs, int failures) {
        sim.heap.add(new SimEvent(timeMs, kind, sim.eventSeq++, arrivalMs, failures));
    }

    /**
     * Poisson arrivals with a per-second rate that follows the burst/spike windows. When the next
     * gap crosses a second boundary the draw restarts there (memoryless), so the rate change takes
     * effect exactly at the boundary. Uses only the arrival stream.
     */
    private void scheduleNextArrival(SimulationState sim, Scenario scenario, double fromMs) {
        double durationMs = scenario.getDurationSeconds() * 1000.0;
        double t = fromMs;
        while (t < durationMs) {
            int second = (int) (t / 1000);
            double perMs = loadAt(sim, scenario, second) * scenario.getRatePerSecond() / 1000.0;
            double boundary = (second + 1) * 1000.0;
            if (perMs <= 0) {
                t = boundary;
                continue;
            }
            double candidate = t - Math.log(1 - sim.arrivalRandom.nextDouble()) / perMs;
            if (candidate < boundary) {
                if (candidate < durationMs) {
                    schedule(sim, SimEvent.Kind.ARRIVAL, candidate, 0, 0);
                }
                return;
            }
            t = boundary;
        }
    }

    private double loadAt(SimulationState sim, Scenario scenario, int second) {
        int duration = scenario.getDurationSeconds();
        if (isBurst(scenario, second)) {
            return 3;
        }
        return sim.disturbance.spikeAt(second, duration) ? sim.disturbance.spikeMultiplier() : 1;
    }

    private boolean isBurst(Scenario scenario, int second) {
        int duration = scenario.getDurationSeconds();
        return scenario.isBurstEnabled() && second > duration * 0.42 && second < duration * 0.57;
    }

    /** Consumers a scripted outage leaves alive in this second (unbounded when there is none). */
    private int consumerCapAt(SimulationState sim, Scenario scenario, BrokerBehavior behavior, int second) {
        int duration = scenario.getDurationSeconds();
        if (!sim.disturbance.outageAt(second, duration)) {
            return Integer.MAX_VALUE;
        }
        int outageStart = (int) (duration * Disturbance.OUTAGE_START);
        boolean rebalancing = second < outageStart + behavior.failoverPauseSeconds();
        return rebalancing ? 0 : Math.max(0, scenario.getConsumers() - sim.disturbance.consumersLost());
    }

    /** The sweep at a one-second boundary: aging/overflow, the tick record, detectors, next second's capacity. */
    private void closeSecond(SimulationState sim, Scenario scenario, BrokerBehavior behavior) {
        int second = sim.t;
        int duration = scenario.getDurationSeconds();
        behavior.sweep(sim, scenario, second);
        resumeProducerIfPossible(sim, scenario, behavior);

        double capacity = sim.servers * 1000.0 / Math.max(1, scenario.getProcessingMs());
        double util = sim.servers > 0 ? Math.min(1, sim.busyMsSecond / (sim.servers * 1000.0)) : 0;
        int backlog = sim.waiting.size();
        int p50;
        int p95;
        int p99;
        if (sim.secondLatencyCount > 0) {
            Arrays.sort(sim.secondLatencies, 0, sim.secondLatencyCount);
            p50 = (int) Math.round(Percentile.of(sim.secondLatencies, sim.secondLatencyCount, 0.50));
            p95 = (int) Math.round(Percentile.of(sim.secondLatencies, sim.secondLatencyCount, 0.95));
            p99 = (int) Math.round(Percentile.of(sim.secondLatencies, sim.secondLatencyCount, 0.99));
        } else {
            // Nothing finished this second: the oldest waiting message is a lower bound on latency.
            int age = backlog > 0 ? (int) Math.round(sim.nowMs - sim.waiting.peekArrival()) : 0;
            p50 = age;
            p95 = age;
            p99 = age;
        }

        sim.ticks.add(new TickResult(
                second, sim.arrivalsSecond, sim.okSecond, sim.failedSecond, sim.droppedSecond, backlog, util,
                p50, p95, p99, capacity));

        detectSaturation(sim, second, util, sim.arrivalsSecond, capacity);
        detectLag(sim, second, scenario);
        detectBurst(sim, second, isBurst(scenario, second), duration);

        sim.arrivalsSecond = 0;
        sim.okSecond = 0;
        sim.failedSecond = 0;
        sim.droppedSecond = 0;
        sim.secondLatencyCount = 0;
        sim.busyMsSecond = 0;

        sim.t++;
        if (sim.t >= duration) {
            sim.done = true;
            String remaining = sim.getQueue() > 0
                    ? Math.round(sim.getQueue()) + " mensagens ficaram na fila."
                    : "Fila vazia ao final.";
            sim.addEvent(new EventResult(duration, EventType.FINISHED, "Simulação encerrada. " + remaining));
            checkInvariants(sim, duration * 1000.0);
        } else {
            sim.consumerCap = consumerCapAt(sim, scenario, behavior, sim.t);
            schedule(sim, SimEvent.Kind.SWEEP, (sim.t + 1) * 1000.0, 0, 0);
            dispatch(sim, scenario, behavior);
        }
    }

    /**
     * Conservation: produced = delivered + pending + dropped + dlq (throws otherwise). Little's law:
     * average messages in the system = arrival rate x mean time in the system; with the exact time
     * integral on the left and per-message sojourns on the right, a gap above 2% is a bookkeeping
     * error, not a modelling one (warning).
     */
    private void checkInvariants(SimulationState sim, double endMs) {
        long pending = sim.getPending();
        long accounted = sim.ok + pending + sim.dropped + sim.dlq;
        if (accounted != sim.produced || sim.inSystem != pending) {
            throw new IllegalStateException(
                    "Conservation violated: produced=" + sim.produced + " delivered=" + sim.ok + " pending=" + pending
                            + " dropped=" + sim.dropped + " dlq=" + sim.dlq + " inSystem=" + sim.inSystem);
        }
        if (sim.produced == 0) {
            return;
        }
        double sojourn = sim.sojournSum + sim.waiting.sumWaitUntil(endMs);
        for (SimEvent e : sim.heap) {
            if (e.kind == SimEvent.Kind.COMPLETION || e.kind == SimEvent.Kind.REAPPEAR) {
                sojourn += endMs - e.arrivalMs;
            }
        }
        double averageInSystem = sim.inSystemArea / endMs;
        double throughputTimesLatency = sojourn / endMs;
        double error = averageInSystem > 0
                ? Math.abs(averageInSystem - throughputTimesLatency) / averageInSystem
                : 0;
        if (error > LITTLE_TOLERANCE) {
            String warning = String.format(
                    "Little's law off by %.1f%% (L=%.3f, lambda*W=%.3f): accounting error", error * 100,
                    averageInSystem, throughputTimesLatency);
            sim.warnings.add(warning);
            log.warn(warning);
        }
    }

    private void detectSaturation(SimulationState sim, int t, double util, int produced, double capacity) {
        if (util > 0.95) {
            sim.satStreak++;
            if (sim.satStreak == 5 && !sim.saturated) {
                sim.saturated = true;
                sim.addEvent(new EventResult(t, EventType.SATURATION,
                        "Consumidores saturados (>95% por 5s). Produção (" + produced
                                + "/s) acima da capacidade (~" + Math.round(capacity) + "/s)."));
            }
        } else {
            if (sim.saturated && util < 0.8) {
                sim.saturated = false;
                sim.addEvent(new EventResult(t, EventType.RECOVERED, "Consumidores voltaram a ter folga; backlog em queda."));
            }
            sim.satStreak = 0;
        }
    }

    private void detectLag(SimulationState sim, int t, Scenario scenario) {
        int rate = scenario.getRatePerSecond();
        if (sim.getQueue() > rate * 10 && !sim.lagWarn) {
            sim.lagWarn = true;
            sim.addEvent(new EventResult(t, EventType.LAG,
                    "Backlog passou de 10s de produção (" + Math.round(sim.getQueue()) + " msgs). Latência vai subir."));
        }
        if (sim.getQueue() < rate && sim.lagWarn && t > 5) {
            sim.lagWarn = false;
            sim.addEvent(new EventResult(t, EventType.RECOVERED, "Backlog drenado para menos de 1s de produção."));
        }
    }

    private void detectBurst(SimulationState sim, int t, boolean burst, int duration) {
        if (burst && !sim.burstSeen) {
            sim.burstSeen = true;
            sim.addEvent(new EventResult(t, EventType.BURST_START, "Pico de tráfego: produção triplicou."));
        }
        if (!burst && sim.burstSeen && !sim.burstEnd && t > duration * 0.5) {
            sim.burstEnd = true;
            sim.addEvent(new EventResult(t, EventType.BURST_END, "Fim do pico de tráfego."));
        }
    }
}
