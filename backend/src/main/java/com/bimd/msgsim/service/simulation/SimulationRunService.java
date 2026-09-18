package com.bimd.msgsim.service.simulation;

import com.bimd.msgsim.domain.dto.EventResponse;
import com.bimd.msgsim.domain.dto.RunSummaryResponse;
import com.bimd.msgsim.domain.dto.TickResponse;
import com.bimd.msgsim.domain.mapper.SimulationRunMapper;
import com.bimd.msgsim.domain.model.RunMode;
import com.bimd.msgsim.domain.model.RunStatus;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.domain.model.SimulationEvent;
import com.bimd.msgsim.domain.model.SimulationRun;
import com.bimd.msgsim.domain.model.SimulationTick;
import com.bimd.msgsim.exception.BusinessException;
import com.bimd.msgsim.repository.ScenarioRepository;
import com.bimd.msgsim.repository.SimulationEventRepository;
import com.bimd.msgsim.repository.SimulationRunRepository;
import com.bimd.msgsim.repository.SimulationTickRepository;
import com.bimd.msgsim.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SimulationRunService {

    private final ScenarioRepository scenarioRepository;
    private final UserRepository userRepository;
    private final SimulationRunRepository runRepository;
    private final SimulationTickRepository tickRepository;
    private final SimulationEventRepository eventRepository;
    private final SimulationEngine engine;
    private final SimulationRunMapper mapper;

    @Transactional
    public RunSummaryResponse runInstant(String ownerEmail, UUID scenarioId, Long requestedSeed) {
        Scenario scenario = findOwnedScenario(ownerEmail, scenarioId);
        long seed = requestedSeed != null ? requestedSeed : System.nanoTime();

        SimulationRun run = new SimulationRun();
        run.setScenario(scenario);
        run.setStatus(RunStatus.RUNNING);
        run.setMode(RunMode.INSTANT);
        run.setSeed(seed);
        run.setStartedAt(Instant.now());
        runRepository.save(run);

        SimulationState state = engine.runToCompletion(scenario, seed);

        List<SimulationTick> ticks = state.getTicks().stream().map(t -> toEntity(run, t)).toList();
        List<SimulationEvent> events = state.getEvents().stream().map(e -> toEntity(run, e)).toList();
        tickRepository.saveAll(ticks);
        eventRepository.saveAll(events);

        run.setStatus(RunStatus.COMPLETED);
        run.setFinishedAt(Instant.now());
        run.setProducedTotal(state.getProduced());
        run.setDeliveredTotal(state.getOk());
        run.setDlqTotal(state.getDlq());
        run.setDroppedTotal(state.getDropped());
        run.setRetriesTotal(state.getRetries());
        runRepository.save(run);

        return mapper.toResponse(run);
    }

    public RunSummaryResponse get(String ownerEmail, UUID runId) {
        return mapper.toResponse(findOwnedRun(ownerEmail, runId));
    }

    public List<TickResponse> listTicks(String ownerEmail, UUID runId) {
        findOwnedRun(ownerEmail, runId);
        return tickRepository.findByRunIdOrderBySecondAsc(runId).stream().map(mapper::toResponse).toList();
    }

    public List<EventResponse> listEvents(String ownerEmail, UUID runId) {
        findOwnedRun(ownerEmail, runId);
        return eventRepository.findByRunIdOrderBySecondAsc(runId).stream().map(mapper::toResponse).toList();
    }

    private SimulationTick toEntity(SimulationRun run, TickResult result) {
        SimulationTick tick = new SimulationTick();
        tick.setRun(run);
        tick.setSecond(result.second());
        tick.setProduced(result.produced());
        tick.setConsumed(result.consumed());
        tick.setFailed(result.failed());
        tick.setDropped(result.dropped());
        tick.setBacklog(result.backlog());
        tick.setUtilization(result.utilization());
        tick.setP50Ms(result.p50Ms());
        tick.setP95Ms(result.p95Ms());
        tick.setP99Ms(result.p99Ms());
        tick.setCapacity(result.capacity());
        return tick;
    }

    private SimulationEvent toEntity(SimulationRun run, EventResult result) {
        SimulationEvent event = new SimulationEvent();
        event.setRun(run);
        event.setSecond(result.second());
        event.setType(result.type());
        event.setMessage(result.message());
        return event;
    }

    private Scenario findOwnedScenario(String ownerEmail, UUID scenarioId) {
        UUID ownerId = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"))
                .getId();
        return scenarioRepository.findByIdAndOwnerId(scenarioId, ownerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Scenario not found"));
    }

    private SimulationRun findOwnedRun(String ownerEmail, UUID runId) {
        UUID ownerId = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"))
                .getId();
        return runRepository.findByIdAndScenarioOwnerId(runId, ownerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Run not found"));
    }
}
