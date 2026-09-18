package com.bimd.msgsim.service.report;

import com.bimd.msgsim.domain.dto.InsightResponse;
import com.bimd.msgsim.domain.dto.ReportResponse;
import com.bimd.msgsim.domain.dto.TradeoffResponse;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.domain.model.SimulationRun;
import com.bimd.msgsim.domain.model.SimulationTick;
import com.bimd.msgsim.exception.BusinessException;
import com.bimd.msgsim.repository.ScenarioRepository;
import com.bimd.msgsim.repository.SimulationTickRepository;
import com.bimd.msgsim.repository.UserRepository;
import com.bimd.msgsim.service.simulation.SimulationRunService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Controller-facing facade: ownership checks + DTO mapping for the analytical report endpoints. */
@Service
@RequiredArgsConstructor
public class ReportQueryService {

    private final ScenarioRepository scenarioRepository;
    private final UserRepository userRepository;
    private final SimulationTickRepository tickRepository;
    private final SimulationRunService runService;
    private final BrokerTradeoffService tradeoffService;
    private final ReportNarrativeService narrativeService;
    private final InsightsBuilder insightsBuilder;
    private final ConclusionBuilder conclusionBuilder;

    public List<TradeoffResponse> tradeoffs(String ownerEmail, UUID scenarioId) {
        Scenario scenario = findOwnedScenario(ownerEmail, scenarioId);
        TradeoffResult result = tradeoffService.evaluate(scenario);
        return result.rows().stream().map(row -> new TradeoffResponse(
                row.model().broker(), row.best(), row.score(), row.model().capacity(), row.model().ratio(),
                row.model().p50Ms(), row.model().p99Ms(), row.model().backlog(), row.model().loss(),
                row.model().lossPct(), row.model().cost(), row.pros(), row.cons())).toList();
    }

    public ReportResponse report(String ownerEmail, UUID runId) {
        SimulationRun run = runService.findOwnedRun(ownerEmail, runId);
        Scenario scenario = run.getScenario();
        List<SimulationTick> ticks = tickRepository.findByRunIdOrderBySecondAsc(runId);
        TradeoffResult tradeoffs = tradeoffService.evaluate(scenario);

        String narrative = narrativeService.narrative(scenario, run, ticks);
        List<InsightResponse> insights = insightsBuilder.build(scenario, run, ticks).stream()
                .map(i -> new InsightResponse(i.tone(), i.text())).toList();
        List<String> conclusion = conclusionBuilder.build(scenario, tradeoffs, run, ticks);

        return new ReportResponse(narrative, insights, conclusion);
    }

    private Scenario findOwnedScenario(String ownerEmail, UUID scenarioId) {
        UUID ownerId = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"))
                .getId();
        return scenarioRepository.findByIdAndOwnerId(scenarioId, ownerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Scenario not found"));
    }
}
