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
    private final SimulationPersistence persistence;
    private final SimulationRunMapper mapper;

    @Transactional
    public RunSummaryResponse runInstant(String ownerEmail, UUID scenarioId, Long requestedSeed) {
        Scenario scenario = findOwnedScenario(ownerEmail, scenarioId);
        if (scenario.getExecutionMode() == com.bimd.msgsim.domain.model.ExecutionMode.REAL) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "execution=REAL requires mode=LIVE");
        }
        SimulationRun run = createRun(scenario, RunMode.INSTANT, requestedSeed);

        SimulationState state = engine.runToCompletion(scenario, run.getSeed());

        persistence.saveTicks(run, state.getTicks());
        persistence.saveEvents(run, state.getEvents());
        persistence.complete(run, RunStatus.COMPLETED, state);

        return mapper.toResponse(run);
    }

    @Transactional
    public RunSummaryResponse createLiveRun(String ownerEmail, UUID scenarioId, Long requestedSeed) {
        Scenario scenario = findOwnedScenario(ownerEmail, scenarioId);
        SimulationRun run = createRun(scenario, RunMode.LIVE, requestedSeed);
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

    public List<RunSummaryResponse> listRunsForScenario(String ownerEmail, UUID scenarioId) {
        findOwnedScenario(ownerEmail, scenarioId);
        return runRepository.findByScenarioIdOrderByStartedAtDesc(scenarioId).stream().map(mapper::toResponse).toList();
    }

    public SimulationRun findOwnedRun(String ownerEmail, UUID runId) {
        UUID ownerId = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"))
                .getId();
        return runRepository.findByIdAndScenarioOwnerId(runId, ownerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Run not found"));
    }

    private SimulationRun createRun(Scenario scenario, RunMode mode, Long requestedSeed) {
        SimulationRun run = new SimulationRun();
        run.setScenario(scenario);
        run.setStatus(RunStatus.RUNNING);
        run.setMode(mode);
        run.setSeed(requestedSeed != null ? requestedSeed : System.nanoTime());
        run.setStartedAt(Instant.now());
        runRepository.save(run);
        return run;
    }

    private Scenario findOwnedScenario(String ownerEmail, UUID scenarioId) {
        UUID ownerId = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"))
                .getId();
        return scenarioRepository.findByIdAndOwnerId(scenarioId, ownerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Scenario not found"));
    }
}
