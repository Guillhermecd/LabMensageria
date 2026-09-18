package com.bimd.msgsim.service.report;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.RunStatus;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.domain.model.SimulationRun;
import com.bimd.msgsim.domain.model.SimulationTick;
import com.bimd.msgsim.service.simulation.broker.BrokerBehavior;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Turns a run's ticks into prose: what happened, in what order, and — combined with the
 * analytical model — whether the configured broker was the right call. Pure text generation,
 * no persistence; {@code ReportQueryService} wires this to the actual run/tradeoff data.
 */
@Service
@RequiredArgsConstructor
public class ReportNarrativeService {

    private static final Map<BrokerType, String> BROKER_NAMES = Map.of(
            BrokerType.KAFKA, "Kafka", BrokerType.RABBITMQ, "RabbitMQ", BrokerType.SQS, "SQS/SNS");

    /** Fixed regardless of server locale — the narrative text itself is always Portuguese. */
    private static String format(String pattern, Object... args) {
        return String.format(Locale.forLanguageTag("pt-BR"), pattern, args);
    }

    private final List<BrokerBehavior> behaviorList;
    private Map<BrokerType, BrokerBehavior> behaviors;

    private Map<BrokerType, BrokerBehavior> behaviors() {
        if (behaviors == null) {
            behaviors = new EnumMap<>(BrokerType.class);
            behaviorList.forEach(b -> behaviors.put(b.type(), b));
        }
        return behaviors;
    }

    public String narrative(Scenario sc, SimulationRun run, List<SimulationTick> ticks) {
        if (ticks.isEmpty()) {
            return "Ainda sem dados. Ao rodar, este bloco descreve fase a fase o que aconteceu com a fila, "
                    + "os consumidores e as mensagens.";
        }
        BrokerBehavior behavior = behaviors().get(sc.getBroker());
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

    public List<Insight> insights(Scenario sc, SimulationRun run, List<SimulationTick> ticks) {
        List<Insight> insights = new ArrayList<>();
        if (ticks.isEmpty()) {
            insights.add(new Insight("info", "Ajuste os parâmetros e rode a simulação para gerar o relatório."));
            return insights;
        }
        BrokerBehavior behavior = behaviors().get(sc.getBroker());
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

    public List<String> conclusion(Scenario sc, TradeoffResult tradeoffs, SimulationRun run, List<SimulationTick> ticks) {
        List<String> out = new ArrayList<>();
        BrokerModel best = tradeoffs.best().model();
        BrokerModel cur = tradeoffs.find(sc.getBroker()).model();

        StringBuilder first = new StringBuilder(format(
                "Para %d msg/s com %d consumidores a %d ms, o modelo aponta %s como a melhor opção (%d pts): "
                        + "capacidade de ~%d msg/s, latência p50 estimada em %d ms e perda esperada de %.2f%%. ",
                sc.getRatePerSecond(), sc.getConsumers(), sc.getProcessingMs(), BROKER_NAMES.get(best.broker()),
                tradeoffs.best().score(), Math.round(best.capacity()), Math.round(best.p50Ms()), best.lossPct()));
        first.append(best.broker() == sc.getBroker()
                ? "É o broker já configurado neste cenário."
                : format("O cenário usa %s (%d pts); a diferença vem principalmente de %s.",
                        BROKER_NAMES.get(sc.getBroker()), tradeoffs.find(sc.getBroker()).score(), mainDelta(cur, best)));
        out.add(first.toString());

        if (!cur.stable()) {
            out.add(format(
                    "Nenhuma troca de broker resolve carga acima da capacidade: com %d msg/s você precisa de pelo "
                            + "menos %d consumidores ativos (para ficar em ~80%%) ou reduzir o processamento para "
                            + "~%d ms.",
                    sc.getRatePerSecond(),
                    (int) Math.ceil(sc.getRatePerSecond() * sc.getProcessingMs() / 1000.0 / 0.8),
                    (int) Math.floor(1000.0 * sc.getConsumers() / sc.getRatePerSecond() * 0.8)));
        } else if (cur.ratio() > 0.8) {
            int newEff = sc.getBroker() == BrokerType.KAFKA && sc.getPartitions() != null
                    ? Math.min(sc.getConsumers() + 1, sc.getPartitions())
                    : sc.getConsumers() + 1;
            double newCap = newEff * (1000.0 / Math.max(1, sc.getProcessingMs()));
            out.add(format("A folga é pequena (%d%%). Um consumidor a mais levaria a ocupação para %d%%.",
                    Math.round((1 - cur.ratio()) * 100), Math.round(sc.getRatePerSecond() / newCap * 100)));
        }

        if (!ticks.isEmpty()) {
            SimulationTick last = ticks.get(ticks.size() - 1);
            long maxBacklog = ticks.stream().mapToInt(SimulationTick::getBacklog).max().orElse(0);
            String modelNote = cur.stable() && Math.abs(last.getP50Ms() - cur.p50Ms()) > cur.p50Ms() * 0.5
                    ? format(" (p50 medido %d ms vs %d ms estimado: o modelo analítico é conservador)",
                            last.getP50Ms(), Math.round(cur.p50Ms()))
                    : "";
            out.add(format(
                    "A simulação rodada confirma o modelo: pico de backlog em %s mensagens, %s perdidas e p95 "
                            + "final de %d ms%s.",
                    fmt(maxBacklog), fmt(run.getDlqTotal() + run.getDroppedTotal()), last.getP95Ms(), modelNote));
        } else {
            out.add("Rode a simulação para confrontar essas estimativas com o comportamento tick a tick.");
        }
        return out;
    }

    private String mainDelta(BrokerModel cur, BrokerModel best) {
        record Delta(double weight, String label) {
        }
        List<Delta> deltas = new ArrayList<>();
        if (cur.stable() != best.stable()) {
            deltas.add(new Delta(1, "estabilidade (carga acima da capacidade)"));
        }
        deltas.add(new Delta(
                Math.abs(cur.p50Ms() - best.p50Ms()) / Math.max(1, best.p50Ms()) * 0.25,
                format("latência (%d vs %d ms)", Math.round(cur.p50Ms()), Math.round(best.p50Ms()))));
        deltas.add(new Delta(
                Math.abs(cur.lossPct() - best.lossPct()) / 5 * 0.20,
                format("perda (%.2f%% vs %.2f%%)", cur.lossPct(), best.lossPct())));
        deltas.add(new Delta(
                Math.abs(cur.cost() - best.cost()) / Math.max(0.001, best.cost()) * 0.10,
                format("custo ($%.3f vs $%.3f)", cur.cost(), best.cost())));
        deltas.add(new Delta(Math.abs(cur.opsScore() - best.opsScore()) * 0.10, "simplicidade operacional"));
        return deltas.stream().max(Comparator.comparingDouble(Delta::weight)).map(Delta::label).orElse("");
    }

    private int visibilityTimeout(Scenario sc) {
        return sc.getVisibilityTimeoutSeconds() != null ? sc.getVisibilityTimeoutSeconds() : 30;
    }

    private String fmt(double n) {
        if (n >= 1e6) return format("%.2fM", n / 1e6);
        if (n >= 1e4) return format("%.1fk", n / 1e3);
        return String.valueOf(Math.round(n));
    }
}
