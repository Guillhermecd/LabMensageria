package com.bimd.msgsim.service.real;

import com.bimd.msgsim.domain.dto.RunSummaryResponse;
import com.bimd.msgsim.domain.dto.TickResponse;
import com.bimd.msgsim.domain.mapper.SimulationRunMapper;
import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.EventType;
import com.bimd.msgsim.domain.model.RunMode;
import com.bimd.msgsim.domain.model.RunStatus;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.domain.model.SimulationRun;
import com.bimd.msgsim.exception.BusinessException;
import com.bimd.msgsim.service.simulation.EventResult;
import com.bimd.msgsim.service.simulation.SimulationPersistence;
import com.bimd.msgsim.service.simulation.SimulationRunService;
import com.bimd.msgsim.service.simulation.TickResult;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Same SSE-stream shape as {@code LiveSimulationService}, but every tick's numbers come from a
 * real RabbitMQ, not {@code SimulationEngine.step()}. Publishing runs on a fixed-rate scheduler;
 * pass/fail/retry/DLQ decisions for each consumed message are simulated here (via the scenario's
 * failurePct/maxRetries/dlqEnabled), same as the engine does, but transport and backlog are real.
 */
@Service
@RequiredArgsConstructor
public class RealSimulationService {

    private static final long TICK_INTERVAL_MS = 1000;
    private static final int TICK_FLUSH_BATCH = 10;
    private static final long DRAIN_WAIT_MS = 500;

    private final SimulationRunService runService;
    private final SimulationPersistence persistence;
    private final SimulationRunMapper mapper;
    private final List<MessageBrokerPort> portImplementations;

    private final Map<UUID, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();
    private final ExecutorService loopExecutor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService publishScheduler = Executors.newScheduledThreadPool(4);

    private Map<BrokerType, MessageBrokerPort> ports;

    private Map<BrokerType, MessageBrokerPort> ports() {
        if (ports == null) {
            ports = new EnumMap<>(BrokerType.class);
            portImplementations.forEach(p -> ports.put(p.type(), p));
        }
        return ports;
    }

    public SseEmitter stream(String ownerEmail, UUID runId) {
        SimulationRun run = runService.findOwnedRun(ownerEmail, runId);
        if (run.getMode() != RunMode.LIVE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Run is not in LIVE mode");
        }
        if (run.getStatus() != RunStatus.RUNNING) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Run already finished");
        }

        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean stopRequested = new AtomicBoolean(false);
        stopFlags.put(runId, stopRequested);
        emitter.onCompletion(() -> stopFlags.remove(runId));
        emitter.onTimeout(() -> stopFlags.remove(runId));

        loopExecutor.submit(() -> runLoop(run, emitter, stopRequested));
        return emitter;
    }

    public void stop(String ownerEmail, UUID runId) {
        runService.findOwnedRun(ownerEmail, runId);
        AtomicBoolean flag = stopFlags.get(runId);
        if (flag != null) {
            flag.set(true);
        }
    }

    @PreDestroy
    void shutdown() {
        loopExecutor.shutdownNow();
        publishScheduler.shutdownNow();
    }

    private void runLoop(SimulationRun run, SseEmitter emitter, AtomicBoolean stopRequested) {
        Scenario scenario = run.getScenario();
        MessageBrokerPort port = ports().get(scenario.getBroker());
        if (port == null) {
            emitter.completeWithError(new BusinessException(
                    HttpStatus.BAD_REQUEST, "No real adapter for broker " + scenario.getBroker()));
            return;
        }
        String queueName = "msgsim-" + run.getId();
        RealMetrics metrics = new RealMetrics();
        double capacity = scenario.getConsumers() * (1000.0 / Math.max(1, scenario.getProcessingMs()));

        List<TickResult> ticks = new ArrayList<>();
        int persistedTicks = 0;
        ScheduledFuture<?> publishTask = null;
        try {
            port.setUp(queueName, scenario);
            port.registerConsumer(queueName, scenario.getConsumers(),
                    (payload, retryCount) -> processMessage(scenario, metrics, payload, retryCount));
            publishTask = publishScheduler.scheduleAtFixedRate(
                    () -> publishBatch(port, queueName, scenario, metrics), 0, 1, TimeUnit.SECONDS);

            int elapsed = 0;
            while (elapsed < scenario.getDurationSeconds() && !stopRequested.get()) {
                Thread.sleep(TICK_INTERVAL_MS);
                elapsed++;
                TickResult tick = metrics.buildTick(elapsed, port.queueDepth(queueName), capacity);
                ticks.add(tick);
                emitter.send(SseEmitter.event().name("tick").data(toResponse(tick), MediaType.APPLICATION_JSON));
                if (ticks.size() - persistedTicks >= TICK_FLUSH_BATCH) {
                    persistence.saveTicks(run, ticks.subList(persistedTicks, ticks.size()));
                    persistedTicks = ticks.size();
                }
            }

            publishTask.cancel(false);
            Thread.sleep(DRAIN_WAIT_MS);
            port.stopConsumer(queueName);

            if (persistedTicks < ticks.size()) {
                persistence.saveTicks(run, ticks.subList(persistedTicks, ticks.size()));
            }
            String remaining = "Execução real encerrada. " + metrics.produced() + " mensagens publicadas, "
                    + metrics.ok() + " entregues, " + metrics.dlq() + " em DLQ.";
            persistence.saveEvents(run, List.of(new EventResult(elapsed, EventType.FINISHED, remaining)));

            RunStatus finalStatus = stopRequested.get() && elapsed < scenario.getDurationSeconds()
                    ? RunStatus.STOPPED : RunStatus.COMPLETED;
            persistence.complete(run, finalStatus,
                    metrics.produced(), metrics.ok(), metrics.dlq(), metrics.dropped(), metrics.retries());

            RunSummaryResponse summary = mapper.toResponse(run);
            emitter.send(SseEmitter.event().name("finished").data(summary, MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception e) {
            if (publishTask != null) {
                publishTask.cancel(true);
            }
            emitter.completeWithError(e);
        } finally {
            port.tearDown(queueName);
            stopFlags.remove(run.getId());
        }
    }

    private void publishBatch(MessageBrokerPort port, String queueName, Scenario scenario, RealMetrics metrics) {
        int count = scenario.getRatePerSecond();
        for (int i = 0; i < count; i++) {
            byte[] payload = ByteBuffer.allocate(Long.BYTES).putLong(Instant.now().toEpochMilli()).array();
            port.publish(queueName, payload, 0);
        }
        metrics.recordProduced(count);
    }

    private ProcessResult processMessage(Scenario scenario, RealMetrics metrics, byte[] payload, int retryCount) {
        try {
            Thread.sleep(scenario.getProcessingMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        double failureRate = scenario.getFailurePct().doubleValue() / 100.0;
        boolean failed = ThreadLocalRandom.current().nextDouble() < failureRate;
        if (!failed) {
            long publishedAt = ByteBuffer.wrap(payload).getLong();
            metrics.recordOk(Instant.now().toEpochMilli() - publishedAt);
            return ProcessResult.OK;
        }
        if (retryCount < scenario.getMaxRetries()) {
            metrics.recordRetry();
            return ProcessResult.RETRY;
        }
        if (scenario.isDlqEnabled()) {
            metrics.recordDlq();
            return ProcessResult.DLQ;
        }
        metrics.recordDropped();
        return ProcessResult.DROP;
    }

    private TickResponse toResponse(TickResult t) {
        return new TickResponse(
                t.second(), t.produced(), t.consumed(), t.failed(), t.dropped(), t.backlog(),
                t.utilization(), t.p50Ms(), t.p95Ms(), t.p99Ms(), t.capacity());
    }
}
