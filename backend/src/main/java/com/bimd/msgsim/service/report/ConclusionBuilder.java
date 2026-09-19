package com.bimd.msgsim.service.report;

import static com.bimd.msgsim.service.report.ReportTextFormat.fmt;
import static com.bimd.msgsim.service.report.ReportTextFormat.format;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.domain.model.SimulationRun;
import com.bimd.msgsim.domain.model.SimulationTick;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Compares the scenario's configured broker against the analytical winner, in prose. */
@Service
public class ConclusionBuilder {

    private static final Map<BrokerType, String> BROKER_NAMES = Map.of(
            BrokerType.KAFKA, "Kafka", BrokerType.RABBITMQ, "RabbitMQ", BrokerType.SQS, "SQS/SNS");

    public List<String> build(Scenario sc, TradeoffResult tradeoffs, SimulationRun run, List<SimulationTick> ticks) {
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
}
