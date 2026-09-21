package com.bimd.msgsim.controller;

import com.bimd.msgsim.domain.dto.BatchSummaryResponse;
import com.bimd.msgsim.domain.dto.CompareResponse;
import com.bimd.msgsim.domain.dto.DecisionResponse;
import com.bimd.msgsim.domain.dto.SuiteResponse;
import com.bimd.msgsim.domain.dto.SweepResponse;
import com.bimd.msgsim.domain.dto.EventResponse;
import com.bimd.msgsim.domain.dto.ReportResponse;
import com.bimd.msgsim.domain.dto.RunSummaryResponse;
import com.bimd.msgsim.domain.dto.TickResponse;
import com.bimd.msgsim.domain.model.ExecutionMode;
import com.bimd.msgsim.domain.model.RunMode;
import com.bimd.msgsim.domain.model.SimulationRun;
import com.bimd.msgsim.service.real.RealSimulationService;
import com.bimd.msgsim.service.report.CompareService;
import com.bimd.msgsim.service.report.DecisionService;
import com.bimd.msgsim.service.report.ScoreWeights;
import com.bimd.msgsim.service.report.SuiteService;
import com.bimd.msgsim.service.report.SweepService;
import com.bimd.msgsim.service.report.ReportQueryService;
import com.bimd.msgsim.service.simulation.BatchService;
import com.bimd.msgsim.service.simulation.LiveSimulationService;
import com.bimd.msgsim.service.simulation.SimulationRunService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class RunController {

    private final SimulationRunService runService;
    private final LiveSimulationService liveSimulationService;
    private final RealSimulationService realSimulationService;
    private final ReportQueryService reportQueryService;
    private final CompareService compareService;
    private final BatchService batchService;
    private final DecisionService decisionService;
    private final SuiteService suiteService;
    private final SweepService sweepService;

    @PostMapping("/api/scenarios/{scenarioId}/runs")
    public ResponseEntity<RunSummaryResponse> createRun(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID scenarioId,
            @RequestParam(defaultValue = "INSTANT") RunMode mode,
            @RequestParam(required = false) Long seed) {
        RunSummaryResponse response = mode == RunMode.LIVE
                ? runService.createLiveRun(principal.getUsername(), scenarioId, seed)
                : runService.runInstant(principal.getUsername(), scenarioId, seed);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/api/scenarios/{scenarioId}/batches")
    public BatchSummaryResponse createBatch(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID scenarioId,
            @RequestParam(defaultValue = "25") int rounds,
            @RequestParam(required = false) Long seed) {
        return batchService.run(principal.getUsername(), scenarioId, rounds, seed);
    }

    /** Weights are relative (rescaled to sum to 1); omitted ones fall back to the defaults. */
    @GetMapping("/api/scenarios/{scenarioId}/decision")
    public DecisionResponse decision(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID scenarioId,
            @RequestParam(defaultValue = "25") int rounds,
            @RequestParam(required = false) Long seed,
            @RequestParam(defaultValue = "0.35") double stability,
            @RequestParam(defaultValue = "0.25") double latency,
            @RequestParam(defaultValue = "0.20") double loss,
            @RequestParam(defaultValue = "0.10") double cost,
            @RequestParam(defaultValue = "0.10") double ops) {
        return decisionService.decide(
                principal.getUsername(), scenarioId, new ScoreWeights(stability, latency, loss, cost, ops), rounds, seed);
    }

    /** Broker x scenario matrix (healthy, half the consumers down, 2x spike, 10% failures). */
    @GetMapping("/api/scenarios/{scenarioId}/suite")
    public SuiteResponse suite(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID scenarioId,
            @RequestParam(defaultValue = "25") int rounds,
            @RequestParam(required = false) Long seed,
            @RequestParam(defaultValue = "0.35") double stability,
            @RequestParam(defaultValue = "0.25") double latency,
            @RequestParam(defaultValue = "0.20") double loss,
            @RequestParam(defaultValue = "0.10") double cost,
            @RequestParam(defaultValue = "0.10") double ops) {
        return suiteService.run(
                principal.getUsername(), scenarioId, new ScoreWeights(stability, latency, loss, cost, ops), rounds, seed);
    }

    /** p99 versus occupancy (50/75/90/95% of each broker's capacity). */
    @GetMapping("/api/scenarios/{scenarioId}/sweep")
    public SweepResponse sweep(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID scenarioId,
            @RequestParam(defaultValue = "25") int rounds,
            @RequestParam(required = false) Long seed) {
        return sweepService.run(principal.getUsername(), scenarioId, rounds, seed);
    }

    @GetMapping("/api/runs/{runId}")
    public RunSummaryResponse get(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID runId) {
        return runService.get(principal.getUsername(), runId);
    }

    @GetMapping("/api/runs/{runId}/ticks")
    public List<TickResponse> listTicks(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID runId) {
        return runService.listTicks(principal.getUsername(), runId);
    }

    @GetMapping("/api/runs/{runId}/events")
    public List<EventResponse> listEvents(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID runId) {
        return runService.listEvents(principal.getUsername(), runId);
    }

    @GetMapping("/api/runs/{runId}/stream")
    public SseEmitter stream(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID runId,
            @RequestParam(defaultValue = "5") int speed) {
        SimulationRun run = runService.findOwnedRun(principal.getUsername(), runId);
        return run.getScenario().getExecutionMode() == ExecutionMode.REAL
                ? realSimulationService.stream(principal.getUsername(), runId)
                : liveSimulationService.stream(principal.getUsername(), runId, speed);
    }

    @PostMapping("/api/runs/{runId}/stop")
    public ResponseEntity<Void> stop(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID runId) {
        SimulationRun run = runService.findOwnedRun(principal.getUsername(), runId);
        if (run.getScenario().getExecutionMode() == ExecutionMode.REAL) {
            realSimulationService.stop(principal.getUsername(), runId);
        } else {
            liveSimulationService.stop(principal.getUsername(), runId);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/runs/{runId}/report")
    public ReportResponse report(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID runId) {
        return reportQueryService.report(principal.getUsername(), runId);
    }

    @GetMapping("/api/runs/compare")
    public CompareResponse compare(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam UUID a,
            @RequestParam UUID b) {
        return compareService.compare(principal.getUsername(), a, b);
    }
}
