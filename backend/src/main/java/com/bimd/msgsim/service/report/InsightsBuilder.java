package com.bimd.msgsim.service.report;

import static com.bimd.msgsim.service.report.ReportTextFormat.fmt;
import static com.bimd.msgsim.service.report.ReportTextFormat.format;
import static com.bimd.msgsim.service.report.ReportTextFormat.visibilityTimeout;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.domain.model.SimulationRun;
import com.bimd.msgsim.domain.model.SimulationTick;
import com.bimd.msgsim.service.simulation.broker.BrokerBehavior;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The "O que está acontecendo" bullet list — short, tone-coded observations about one run. */
@Service
@RequiredArgsConstructor
public class InsightsBuilder {

    private final BrokerBehaviors behaviors;

    public List<Insight> build(Scenario sc, SimulationRun run, List<SimulationTick> ticks) {
        List<Insight> insights = new ArrayList<>();
        if (ticks.isEmpty()) {
            insights.add(new Insight("info", "Ajuste os parâmetros e rode a simulação para gerar o relatório."));
            return insights;
        }
        BrokerBehavior behavior = behaviors.get(sc.getBroker());
        int eff = behavior.effectiveConsumers(sc);
        double capacity = eff * (1000.0 / Math.max(1, sc.getProcessingMs()));
        double ratio = sc.getRatePerSecond() / capacity;

        if (ratio > 1) {
            insights.add(new Insight("danger", format(
                    "A produção (%d/s) supera a capacidade de consumo (~%d/s). O backlog cresce sem parar: ou "
                            + "adicione consumidores, ou reduza o tempo de processamento.",
                    sc.getRatePerSecond(), Math.round(capacity))));
        } else if (ratio > 0.8) {
            insights.add(new Insight("warning", format(
                    "Pouca folga (%d%%). Qualquer pico ou lentidão vira backlog e latência.",
                    Math.round((1 - ratio) * 100))));
        } else {
            insights.add(new Insight("ok", format(
                    "Capacidade confortável: consumo %.1f× maior que a produção.", 1 / ratio)));
        }

        if (sc.getBroker() == BrokerType.KAFKA && sc.getPartitions() != null && sc.getConsumers() > sc.getPartitions()) {
            insights.add(new Insight("warning", format(
                    "%d consumidor(es) ociosos: no Kafka cada partição só é lida por um consumidor do grupo. "
                            + "Aumente as partições para escalar.",
                    sc.getConsumers() - sc.getPartitions())));
        }
        if (run.getDroppedTotal() > 0) {
            insights.add(new Insight("info", format(
                    "%s mensagens descartadas por overflow da fila. No RabbitMQ, fila com limite descarta quando "
                            + "enche (ou rejeita publish, se configurado).",
                    fmt(run.getDroppedTotal()))));
        }
        if (run.getDlqTotal() > 0) {
            insights.add(new Insight("danger", format(
                    "%s mensagens foram para a DLQ após %d retries. Vale inspecionar o padrão dos erros antes de "
                            + "reprocessar.",
                    fmt(run.getDlqTotal()), sc.getMaxRetries())));
        }
        if (!sc.isDlqEnabled() && sc.getFailurePct().doubleValue() > 0) {
            insights.add(new Insight("warning",
                    "Sem DLQ, mensagens que falham voltam à fila indefinidamente e ocupam capacidade dos "
                            + "consumidores (poison pill)."));
        }
        if (sc.getBroker() == BrokerType.SQS && sc.getFailurePct().doubleValue() > 0) {
            insights.add(new Insight("info", format(
                    "No SQS, cada falha só volta após o visibility timeout (%ds), o que estica a cauda p99.",
                    visibilityTimeout(sc))));
        }
        if (sc.isBurstEnabled() && ticks.size() > sc.getDurationSeconds() * 0.57) {
            long backlogMax = ticks.stream().mapToInt(SimulationTick::getBacklog).max().orElse(0);
            insights.add(new Insight("info", format(
                    "Durante o pico, o backlog subiu até %s. Repare quanto tempo levou para drenar depois.",
                    fmt(backlogMax))));
        }
        return insights;
    }
}
