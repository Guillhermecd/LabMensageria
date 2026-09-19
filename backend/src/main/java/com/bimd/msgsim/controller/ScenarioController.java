package com.bimd.msgsim.controller;

import com.bimd.msgsim.domain.dto.RunSummaryResponse;
import com.bimd.msgsim.domain.dto.ScenarioRequest;
import com.bimd.msgsim.domain.dto.ScenarioResponse;
import com.bimd.msgsim.domain.dto.TradeoffResponse;
import com.bimd.msgsim.service.report.ReportQueryService;
import com.bimd.msgsim.service.scenario.ScenarioService;
import com.bimd.msgsim.service.simulation.SimulationRunService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scenarios")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioService scenarioService;
    private final ReportQueryService reportQueryService;
    private final SimulationRunService runService;

    @GetMapping
    public List<ScenarioResponse> list(@AuthenticationPrincipal UserDetails principal) {
        return scenarioService.listForOwner(principal.getUsername());
    }

    @PostMapping
    public ResponseEntity<ScenarioResponse> create(
            @AuthenticationPrincipal UserDetails principal, @Valid @RequestBody ScenarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scenarioService.create(principal.getUsername(), request));
    }

    @GetMapping("/{id}")
    public ScenarioResponse get(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID id) {
        return scenarioService.get(principal.getUsername(), id);
    }

    @PutMapping("/{id}")
    public ScenarioResponse update(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id,
            @Valid @RequestBody ScenarioRequest request) {
        return scenarioService.update(principal.getUsername(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID id) {
        scenarioService.delete(principal.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ScenarioResponse> duplicate(
            @AuthenticationPrincipal UserDetails principal, @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scenarioService.duplicate(principal.getUsername(), id));
    }

    @GetMapping("/{id}/tradeoffs")
    public List<TradeoffResponse> tradeoffs(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID id) {
        return reportQueryService.tradeoffs(principal.getUsername(), id);
    }

    @GetMapping("/{id}/runs")
    public List<RunSummaryResponse> runHistory(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID id) {
        return runService.listRunsForScenario(principal.getUsername(), id);
    }
}
