package com.bimd.msgsim.service.report;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Scores Kafka/RabbitMQ/SQS against each other for the same scenario. The weights are
 * named constants, not magic numbers, because the score is the one number the report's
 * conclusion leans on — anyone reading this later needs to see what "best" means.
 */
@Service
@RequiredArgsConstructor
public class BrokerTradeoffService {

    private static final double STABILITY_WEIGHT = 0.35;
    private static final double LATENCY_WEIGHT = 0.25;
    private static final double LOSS_WEIGHT = 0.20;
    private static final double COST_WEIGHT = 0.10;
    private static final double OPS_WEIGHT = 0.10;
    private static final double MAX_ACCEPTABLE_LOSS_PCT = 5.0;

    private final AnalyticalQueueModel model;

    /** Fixed regardless of server locale — the surrounding text is always Portuguese. */
    private static String format(String pattern, Object... args) {
        return String.format(Locale.forLanguageTag("pt-BR"), pattern, args);
    }

    public TradeoffResult evaluate(Scenario scenario) {
        List<BrokerModel> models = Arrays.stream(BrokerType.values())
                .map(broker -> model.compute(scenario, broker))
                .toList();

        double[] stability = models.stream()
                .mapToDouble(m -> m.stable() ? 1 - Math.min(1, m.ratio()) * 0.5 : 0)
                .toArray();
        double[] p50s = models.stream().mapToDouble(BrokerModel::p50Ms).toArray();
        double[] costs = models.stream().mapToDouble(BrokerModel::cost).toArray();

        record Scored(BrokerModel model, int score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < models.size(); i++) {
            BrokerModel m = models.get(i);
            double latencyNorm = normalizeInverted(m.p50Ms(), p50s);
            double lossNorm = 1 - Math.min(1, m.lossPct() / MAX_ACCEPTABLE_LOSS_PCT);
            double costNorm = normalizeInverted(m.cost(), costs);
            int score = (int) Math.round(100 * (
                    STABILITY_WEIGHT * stability[i]
                            + LATENCY_WEIGHT * latencyNorm
                            + LOSS_WEIGHT * lossNorm
                            + COST_WEIGHT * costNorm
                            + OPS_WEIGHT * m.opsScore()));
            scored.add(new Scored(m, score));
        }

        Scored bestScored = scored.stream().max(Comparator.comparingInt(Scored::score)).orElseThrow();
        List<BrokerScore> rows = scored.stream()
                .map(s -> new BrokerScore(
                        s.model(), s.score(), s.model().broker() == bestScored.model().broker(),
                        pros(s.model()), cons(s.model(), scenario)))
                .toList();
        BrokerScore best = rows.stream().filter(BrokerScore::best).findFirst().orElseThrow();

        return new TradeoffResult(rows, best);
    }

    private double normalizeInverted(double value, double[] all) {
        double min = Arrays.stream(all).min().orElse(0);
        double max = Arrays.stream(all).max().orElse(0);
        if (max == min) return 1;
        return 1 - (value - min) / (max - min);
    }

    private List<String> pros(BrokerModel m) {
        return switch (m.broker()) {
            case KAFKA -> List.of(
                    "Retenção em log: nada é descartado por fila cheia; dá para reprocessar.",
                    "Escala horizontal previsível via partições.");
            case RABBITMQ -> List.of(
                    "Menor overhead por mensagem; retry imediato com nack.",
                    "Roteamento flexível (exchanges, bindings).");
            case SQS -> List.of(
                    "Gerenciado: sem broker para operar, escala sozinho.",
                    "DLQ nativa via maxReceiveCount.");
        };
    }

    private List<String> cons(BrokerModel m, Scenario scenario) {
        List<String> cons = new ArrayList<>();
        switch (m.broker()) {
            case KAFKA -> {
                if (m.idleConsumers() > 0) {
                    cons.add((int) m.idleConsumers() + " consumidor(es) ociosos: só "
                            + scenario.getPartitions() + " partições.");
                }
                cons.add("Mais peças para operar (brokers, partições, offsets).");
            }
            case RABBITMQ -> {
                Integer capacity = scenario.getQueueCapacity();
                if (capacity != null && capacity > 0) {
                    cons.add("Fila limitada a " + capacity + ": excedente é descartado.");
                } else {
                    cons.add("Sem limite, a fila cresce na memória/disco do broker.");
                }
                if (scenario.getRatePerSecond() > 2000) {
                    cons.add("Throughput alto pressiona um único nó.");
                }
            }
            case SQS -> {
                Integer visibilityTimeout = scenario.getVisibilityTimeoutSeconds();
                cons.add("Retry só após visibility timeout (" + (visibilityTimeout != null ? visibilityTimeout : 30)
                        + "s): cauda longa.");
                cons.add("Sem ordenação global (fila padrão); custo cresce por request.");
            }
        }
        if (!m.stable()) {
            cons.add(0, "Carga " + format("%.2f", m.ratio()) + "× a capacidade: backlog cresce indefinidamente.");
        }
        return cons;
    }
}
