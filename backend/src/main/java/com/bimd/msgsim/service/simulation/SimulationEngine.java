package com.bimd.msgsim.service.simulation;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.EventType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.service.simulation.broker.BrokerBehavior;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Advances a {@link SimulationState} one second at a time. Broker-specific rules are
 * delegated to {@link BrokerBehavior} so this class never changes when a broker is
 * added (Open/Closed) — see {@code docs/02-simulation-engine.md} for the tick anatomy.
 */
@Component
public class SimulationEngine {

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
        SimulationState state = newState(seed);
        while (!state.isDone()) {
            step(state, scenario);
        }
        return state;
    }

    public void step(SimulationState sim, Scenario scenario) {
        BrokerBehavior behavior = behaviors.get(scenario.getBroker());
        int t = sim.t;
        int duration = scenario.getDurationSeconds();
        boolean burst = scenario.isBurstEnabled() && t > duration * 0.42 && t < duration * 0.57;

        int produced = (int) Math.round(
                scenario.getRatePerSecond() * (burst ? 3 : 1) * (0.85 + sim.random.nextDouble() * 0.3));
        int effectiveConsumers = behavior.effectiveConsumers(scenario);
        double capacity = effectiveConsumers * (1000.0 / Math.max(1, scenario.getProcessingMs()))
                * (0.9 + sim.random.nextDouble() * 0.2);

        double carried = sim.getQueue();
        sim.produced += produced;
        sim.setQueue(sim.getQueue() + produced);

        int droppedThisTick = behavior.applyOverflow(sim, scenario, t);

        double before = sim.getQueue();
        int attempts = (int) Math.min(sim.getQueue(), Math.round(capacity));
        sim.setQueue(sim.getQueue() - attempts);

        double failureRate = scenario.getFailurePct().doubleValue() / 100.0;
        int failed = (int) Math.round(attempts * failureRate * (0.7 + sim.random.nextDouble() * 0.6));
        int ok = attempts - failed;
        sim.ok += ok;

        int toDlq = 0;
        int retried = failed;
        if (scenario.isDlqEnabled()) {
            toDlq = (int) Math.round(
                    failed * Math.pow(Math.max(failureRate, 0.02), Math.max(0, scenario.getMaxRetries() - 1)));
            retried = failed - toDlq;
        }
        if (scenario.getMaxRetries() == 0 && scenario.isDlqEnabled()) {
            toDlq = failed;
            retried = 0;
        }
        sim.dlq += toDlq;
        sim.retries += retried;

        int visibilityDelay = behavior.retryDelaySeconds(scenario);
        if (retried > 0) {
            if (visibilityDelay > 1) {
                sim.retryBuckets.add(new RetryBucket(t + visibilityDelay, retried));
            } else {
                sim.setQueue(sim.getQueue() + retried);
            }
        }
        drainDueRetries(sim, t);

        if (toDlq > 0 && !sim.dlqSeen) {
            sim.dlqSeen = true;
            sim.addEvent(new EventResult(t, EventType.FIRST_DLQ, behavior.firstDlqMessage()));
        }

        double util = capacity > 0 ? Math.min(1, attempts / capacity) : 0;
        detectSaturation(sim, t, util, produced, capacity);
        detectLag(sim, t, scenario);
        detectBurst(sim, t, burst, duration);

        double wait = capacity > 0 ? (Math.min(carried, before) / capacity) * 1000 : 0;
        int overhead = behavior.latencyOverheadMs();
        int processingMs = scenario.getProcessingMs();
        double p50 = processingMs + wait * 0.6 + overhead;
        double p95 = processingMs * 1.9 + wait * 1.1
                + (retried > 0 ? (retried / (double) Math.max(1, attempts)) * processingMs * 10
                        + visibilityDelay * 1000.0 * failureRate : 0);
        double p99 = processingMs * 2.8 + wait * 1.4
                + (failed / (double) Math.max(1, attempts)) * (visibilityDelay * 1000.0 + processingMs * 20);

        sim.getTicks().add(new TickResult(
                t, produced, ok, failed, droppedThisTick, (int) Math.round(sim.getQueue()), util,
                (int) Math.round(p50), (int) Math.round(p95), (int) Math.round(p99), capacity));

        sim.t++;
        if (sim.t >= duration) {
            sim.done = true;
            String remaining = sim.getQueue() > 0
                    ? Math.round(sim.getQueue()) + " mensagens ficaram na fila."
                    : "Fila vazia ao final.";
            sim.addEvent(new EventResult(duration, EventType.FINISHED, "Simulação encerrada. " + remaining));
        }
    }

    private void drainDueRetries(SimulationState sim, int t) {
        List<RetryBucket> remaining = new ArrayList<>();
        for (RetryBucket bucket : sim.retryBuckets) {
            if (bucket.at() <= t + 1) {
                sim.setQueue(sim.getQueue() + bucket.count());
            } else {
                remaining.add(bucket);
            }
        }
        sim.retryBuckets.clear();
        sim.retryBuckets.addAll(remaining);
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
