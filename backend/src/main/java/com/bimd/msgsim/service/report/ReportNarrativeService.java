package com.bimd.msgsim.service.report;

import static com.bimd.msgsim.service.report.ReportTextFormat.fmt;
import static com.bimd.msgsim.service.report.ReportTextFormat.format;
import static com.bimd.msgsim.service.report.ReportTextFormat.visibilityTimeout;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.RunStatus;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.domain.model.SimulationRun;
import com.bimd.msgsim.domain.model.SimulationTick;
import com.bimd.msgsim.service.simulation.broker.BrokerBehavior;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Turns a run's ticks into prose: what happened, in what order. Pure text generation,
 * no persistence; {@code ReportQueryService} wires this to the actual run data.
 */
@Service
@RequiredArgsConstructor
public class ReportNarrativeService {

    private final BrokerBehaviors behaviors;

    public String narrative(Scenario sc, SimulationRun run, List<SimulationTick> ticks) {
        if (ticks.isEmpty()) {
            return "Ainda sem dados. Ao rodar, este bloco descreve fase a fase o que aconteceu com a fila, "
                    + "os consumidores e as mensagens.";
        }
        BrokerBehavior behavior = behaviors.get(sc.getBroker());
        int eff = behavior.effectiveConsumers(sc);
        double capacity = eff * (1000.0 / Math.max(1, sc.getProcessingMs()));

        List<String> parts = new ArrayList<>();
        parts.add(format(
                "Os produtores publicam ~%d msg/s e %d consumidor(es) com %d ms por mensagem conseguem processar "
                        + "até ~%s msg/s (%.2f× de ocupação).",
                sc.getRatePerSecond(), eff, sc.getProcessingMs(), fmt(Math.round(capacity)),
                sc.getRatePerSecond() / capacity));

        SimulationTick firstLag = ticks.stream()
                .filter(t -> t.getBacklog() > sc.getRatePerSecond() * 5)
                .findFirst().orElse(null);
        SimulationTick peak = ticks.stream().max(Comparator.comparingInt(SimulationTick::getBacklog)).orElseThrow();

        if (firstLag != null) {
            parts.add(format(
                    "Aos %ds o backlog passou de 5s de produção; o pico foi de %s mensagens aos %ds, quando a "
                            + "latência p95 chegou a %s ms.",
                    firstLag.getSecond(), fmt(peak.getBacklog()), peak.getSecond(), fmt(peak.getP95Ms())));
            int peakIndex = ticks.indexOf(peak);
            SimulationTick drained = ticks.subList(peakIndex, ticks.size()).stream()
                    .filter(t -> t.getBacklog() < sc.getRatePerSecond())
                    .findFirst().orElse(null);
            parts.add(drained != null
                    ? format("O backlog foi drenado aos %ds, %ds depois do pico.",
                            drained.getSecond(), drained.getSecond() - peak.getSecond())
                    : "Até o fim da simulação o backlog não foi drenado.");
        } else {
            double avgP50 = ticks.stream().mapToInt(SimulationTick::getP50Ms).average().orElse(0);
            parts.add(format(
                    "A fila nunca acumulou mais que %s mensagens: os consumidores acompanharam a produção e a "
                            + "latência p50 ficou em torno de %d ms.",
                    fmt(peak.getBacklog()), Math.round(avgP50)));
        }

        if (run.getDroppedTotal() > 0) {
            parts.add(format("%s mensagens foram descartadas porque a fila atingiu o limite de %d.",
                    fmt(run.getDroppedTotal()), sc.getQueueCapacity()));
        }
        if (run.getRetriesTotal() > 0) {
            String dlqPart = run.getDlqTotal() > 0
                    ? format(" e %s mensagens acabaram na DLQ", fmt(run.getDlqTotal())) : "";
            String sqsPart = sc.getBroker() == BrokerType.SQS
                    ? format(", cada uma esperando %ds de visibility timeout", visibilityTimeout(sc)) : "";
            parts.add(format("Falhas geraram %s retries%s%s.", fmt(run.getRetriesTotal()), dlqPart, sqsPart));
        }

        SimulationTick last = ticks.get(ticks.size() - 1);
        if (run.getStatus() == RunStatus.COMPLETED) {
            double pct = run.getProducedTotal() > 0 ? (double) run.getDeliveredTotal() / run.getProducedTotal() * 100 : 0;
            String pending = last.getBacklog() > 0
                    ? format(" e %s ficaram pendentes", fmt(last.getBacklog())) : "";
            parts.add(format("Ao final, %s de %s mensagens foram entregues (%.1f%%)%s.",
                    fmt(run.getDeliveredTotal()), fmt(run.getProducedTotal()), pct, pending));
        } else {
            parts.add(format("Neste momento (%ds) há %s mensagens na fila e os consumidores estão a %d%%.",
                    last.getSecond(), fmt(last.getBacklog()), Math.round(last.getUtilization() * 100)));
        }
        return String.join(" ", parts);
    }
}
