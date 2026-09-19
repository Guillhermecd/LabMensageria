package com.bimd.msgsim.service.simulation;

import com.bimd.msgsim.domain.dto.EventResponse;
import com.bimd.msgsim.domain.dto.RunSummaryResponse;
import com.bimd.msgsim.domain.dto.TickResponse;
import com.bimd.msgsim.domain.mapper.SimulationRunMapper;
import com.bimd.msgsim.domain.model.RunMode;
import com.bimd.msgsim.domain.model.RunStatus;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.domain.model.SimulationRun;
import com.bimd.msgsim.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Streams a simulation tick by tick over SSE. Each stream runs on its own background
 * thread; {@link #stop} just flips a flag the loop checks between ticks — no thread
 * interruption, so a tick already in flight always finishes and gets persisted.
 */
@Service
@RequiredArgsConstructor
public class LiveSimulationService {

    private static final int TICK_FLUSH_BATCH = 10;
    private static final long TICK_INTERVAL_MS = 100;

    private final SimulationEngine engine;
    private final SimulationPersistence persistence;
    private final SimulationRunMapper mapper;
    private final SimulationRunService runService;

    private final Map<UUID, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public SseEmitter stream(String ownerEmail, UUID runId, int speed) {
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

        int clampedSpeed = Math.clamp(speed, 1, 20);
        executor.submit(() -> runLoop(run, emitter, stopRequested, clampedSpeed));
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
        executor.shutdownNow();
    }

    private void runLoop(SimulationRun run, SseEmitter emitter, AtomicBoolean stopRequested, int speed) {
        Scenario scenario = run.getScenario();
        SimulationState state = engine.newState(run.getSeed());
        int sentTicks = 0;
        int sentEvents = 0;
        int persistedTicks = 0;
        int persistedEvents = 0;

        try {
            while (!state.isDone() && !stopRequested.get()) {
                for (int i = 0; i < speed && !state.isDone(); i++) {
                    engine.step(state, scenario);
                }
                sentTicks = emitNew(emitter, "tick", state.getTicks(), sentTicks, this::toResponse);
                sentEvents = emitNew(emitter, "event", state.getEvents(), sentEvents, this::toResponse);

                if (state.getTicks().size() - persistedTicks >= TICK_FLUSH_BATCH || state.isDone()) {
                    persistence.saveTicks(run, state.getTicks().subList(persistedTicks, state.getTicks().size()));
                    persistence.saveEvents(run, state.getEvents().subList(persistedEvents, state.getEvents().size()));
                    persistedTicks = state.getTicks().size();
                    persistedEvents = state.getEvents().size();
                }
                if (!state.isDone() && !stopRequested.get()) {
                    Thread.sleep(TICK_INTERVAL_MS);
                }
            }
            if (persistedTicks < state.getTicks().size() || persistedEvents < state.getEvents().size()) {
                persistence.saveTicks(run, state.getTicks().subList(persistedTicks, state.getTicks().size()));
                persistence.saveEvents(run, state.getEvents().subList(persistedEvents, state.getEvents().size()));
            }
            RunStatus finalStatus = stopRequested.get() && !state.isDone() ? RunStatus.STOPPED : RunStatus.COMPLETED;
            persistence.complete(run, finalStatus, state);

            RunSummaryResponse summary = mapper.toResponse(run);
            emitter.send(SseEmitter.event().name("finished").data(summary, MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        } finally {
            stopFlags.remove(run.getId());
        }
    }

    private <T> int emitNew(
            SseEmitter emitter, String eventName, List<T> all, int alreadySent, java.util.function.Function<T, Object> toDto)
            throws java.io.IOException {
        List<T> pending = all.subList(alreadySent, all.size());
        for (T item : pending) {
            emitter.send(SseEmitter.event().name(eventName).data(toDto.apply(item), MediaType.APPLICATION_JSON));
        }
        return all.size();
    }

    private TickResponse toResponse(TickResult t) {
        return new TickResponse(
                t.second(), t.produced(), t.consumed(), t.failed(), t.dropped(), t.backlog(),
                t.utilization(), t.p50Ms(), t.p95Ms(), t.p99Ms(), t.capacity());
    }

    private EventResponse toResponse(EventResult e) {
        return new EventResponse(e.second(), e.type(), e.message());
    }
}
