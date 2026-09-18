package com.bimd.msgsim.controller;

import com.bimd.msgsim.domain.dto.EventResponse;
import com.bimd.msgsim.domain.dto.RunSummaryResponse;
import com.bimd.msgsim.domain.dto.TickResponse;
import com.bimd.msgsim.domain.model.RunMode;
import com.bimd.msgsim.exception.BusinessException;
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

@RestController
@RequiredArgsConstructor
public class RunController {

    private final SimulationRunService runService;

    @PostMapping("/api/scenarios/{scenarioId}/runs")
    public ResponseEntity<RunSummaryResponse> createRun(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID scenarioId,
            @RequestParam(defaultValue = "INSTANT") RunMode mode,
            @RequestParam(required = false) Long seed) {
        if (mode != RunMode.INSTANT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "mode=LIVE is not available yet");
        }
        RunSummaryResponse response = runService.runInstant(principal.getUsername(), scenarioId, seed);
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
}
