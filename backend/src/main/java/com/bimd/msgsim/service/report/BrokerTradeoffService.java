package com.bimd.msgsim.service.report;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Scores Kafka/RabbitMQ/SQS against each other for the same scenario, analytically. The weights and
 * formula live in {@link ScoreWeights} and {@link ScoreCalculator}, because the score is the one number the
 * report leans on: anyone reading this later needs to see what "best" means.
 */
@Service
@RequiredArgsConstructor
public class BrokerTradeoffService {

    private final AnalyticalQueueModel model;

    /** Fixed regardless of server locale — the surrounding text is always Portuguese. */
    private static String format(String pattern, Object... args) {
        return String.format(Locale.forLanguageTag("pt-BR"), pattern, args);
    }

    public TradeoffResult evaluate(Scenario scenario) {
        List<BrokerModel> models = Arrays.stream(BrokerType.values())
                .map(broker -> model.compute(scenario, broker))
                .toList();

        Map<BrokerType, ScoreCalculator.Points> points = ScoreCalculator.score(
                models.stream().map(BrokerTradeoffService::inputs).toList(), ScoreWeights.DEFAULT);

        record Scored(BrokerModel model, int score) {
        }
        List<Scored> scored = models.stream()
                .map(m -> new Scored(m, (int) Math.round(points.get(m.broker()).total())))
                .toList();

        Scored bestScored = scored.stream().max(Comparator.comparingInt(Scored::score)).orElseThrow();
        List<BrokerScore> rows = scored.stream()
                .map(s -> new BrokerScore(
                        s.model(), s.score(), s.model().broker() == bestScored.model().broker(),
                        pros(s.model()), cons(s.model(), scenario)))
                .toList();
        BrokerScore best = rows.stream().filter(BrokerScore::best).findFirst().orElseThrow();

        return new TradeoffResult(rows, best);
    }

    /** Raw score inputs of an analytical estimate; shared with the simulation-backed decision. */
    static ScoreCalculator.Inputs inputs(BrokerModel m) {
        return new ScoreCalculator.Inputs(
                m.broker(), ScoreCalculator.stability(m.stable(), m.ratio()), m.p99Ms(), m.lossPct(), m.cost(),
                m.opsScore());
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
