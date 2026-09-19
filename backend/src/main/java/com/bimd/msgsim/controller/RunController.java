package com.bimd.msgsim.controller;

import com.bimd.msgsim.domain.dto.CompareResponse;
import com.bimd.msgsim.domain.dto.EventResponse;
import com.bimd.msgsim.domain.dto.ReportResponse;
import com.bimd.msgsim.domain.dto.RunSummaryResponse;
import com.bimd.msgsim.domain.dto.TickResponse;
import com.bimd.msgsim.domain.model.RunMode;
import com.bimd.msgsim.service.report.CompareService;
import com.bimd.msgsim.service.report.ReportQueryService;
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
    private final ReportQueryService reportQueryService;
    private final CompareService compareService;

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
        return liveSimulationService.stream(principal.getUsername(), runId, speed);
    }

    @PostMapping("/api/runs/{runId}/stop")
    public ResponseEntity<Void> stop(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID runId) {
        liveSimulationService.stop(principal.getUsername(), runId);
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
