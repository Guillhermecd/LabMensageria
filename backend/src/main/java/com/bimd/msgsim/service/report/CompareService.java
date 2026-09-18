package com.bimd.msgsim.service.report;

import com.bimd.msgsim.domain.dto.CompareResponse;
import com.bimd.msgsim.domain.dto.RunReportResponse;
import com.bimd.msgsim.domain.mapper.ScenarioMapper;
import com.bimd.msgsim.domain.mapper.SimulationRunMapper;
import com.bimd.msgsim.domain.model.SimulationRun;
import com.bimd.msgsim.domain.model.SimulationTick;
import com.bimd.msgsim.repository.SimulationEventRepository;
import com.bimd.msgsim.repository.SimulationTickRepository;
import com.bimd.msgsim.service.simulation.SimulationRunService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Puts two runs side by side. Reuses the same DTOs the single-run report uses
 * (Fase 3) so the frontend renders both columns with the exact same components —
 * comparison is a layout concern here, not a new data shape.
 */
@Service
@RequiredArgsConstructor
public class CompareService {

    private final SimulationRunService runService;
    private final SimulationTickRepository tickRepository;
    private final SimulationEventRepository eventRepository;
    private final ScenarioMapper scenarioMapper;
    private final SimulationRunMapper runMapper;

    public CompareResponse compare(String ownerEmail, UUID runIdA, UUID runIdB) {
        RunReportResponse a = loadReport(ownerEmail, runIdA);
        RunReportResponse b = loadReport(ownerEmail, runIdB);
        String comparison = compareText(a, b);
        return new CompareResponse(a, b, comparison);
    }

    private RunReportResponse loadReport(String ownerEmail, UUID runId) {
        SimulationRun run = runService.findOwnedRun(ownerEmail, runId);
        List<SimulationTick> ticks = tickRepository.findByRunIdOrderBySecondAsc(runId);
        return new RunReportResponse(
                scenarioMapper.toResponse(run.getScenario()),
                runMapper.toResponse(run),
                ticks.stream().map(runMapper::toResponse).toList(),
                eventRepository.findByRunIdOrderBySecondAsc(runId).stream().map(runMapper::toResponse).toList());
    }

    private String compareText(RunReportResponse a, RunReportResponse b) {
        if (a.ticks().isEmpty() || b.ticks().isEmpty()) {
            return "Rode as duas simulações até o fim para comparar entrega e latência.";
        }
        double okA = a.run().producedTotal() > 0
                ? (double) a.run().deliveredTotal() / a.run().producedTotal() : 0;
        double okB = b.run().producedTotal() > 0
                ? (double) b.run().deliveredTotal() / b.run().producedTotal() : 0;
        int p95A = a.ticks().get(a.ticks().size() - 1).p95Ms();
        int p95B = b.ticks().get(b.ticks().size() - 1).p95Ms();

        String nameA = a.scenario().name();
        String nameB = b.scenario().name();
        String verdict;
        if (okA >= okB && p95A <= p95B) {
            verdict = format("“%s” se comporta melhor nos dois critérios.", nameA);
        } else if (okA < okB && p95A > p95B) {
            verdict = format("“%s” se comporta melhor nos dois critérios.", nameB);
        } else {
            verdict = "Cada um vence em um critério: escolha conforme o que importa mais, entrega ou latência.";
        }
        return format("Comparando “%s” com “%s”: entrega de %.1f%% vs %.1f%%, p95 de %d ms vs %d ms. %s",
                nameA, nameB, okA * 100, okB * 100, p95A, p95B, verdict);
    }

    private static String format(String pattern, Object... args) {
        return String.format(Locale.forLanguageTag("pt-BR"), pattern, args);
    }
}
