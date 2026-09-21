package com.bimd.msgsim.service.report;

import com.bimd.msgsim.domain.dto.DecisionResponse.Saturation;
import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.service.simulation.RunStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Describes a broker that is offered more than it can process. In that regime p50, p99 and the
 * final backlog only measure how long the run lasted (double the duration, roughly double the
 * numbers), so the report shows what does not depend on it: the deficit, how soon the ceiling is
 * hit, how the broker fails, what it costs and what would fix it.
 */
final class SaturationAdvisor {

    static final String DROP = "DROP";
    static final String PRODUCER_BLOCKED = "PRODUCER_BLOCKED";
    static final String INFLIGHT_EXHAUSTED = "INFLIGHT_EXHAUSTED";
    static final String UNBOUNDED_BACKLOG = "UNBOUNDED_BACKLOG";
    /** Target occupation the recommendation aims for. */
    private static final double TARGET_OCCUPANCY = 0.8;

    private SaturationAdvisor() {
    }

    static Saturation analyse(
            Scenario scenario, BrokerModel model, OptionalLong ceiling, List<RunStatistics> rounds) {
        double rate = scenario.getRatePerSecond();
        double deficit = rate - model.capacity();

        List<Double> hits = rounds.stream()
                .map(RunStatistics::ceilingSecond)
                .filter(second -> second >= 0)
                .sorted()
                .toList();
        boolean withinRun = !hits.isEmpty();
        Double secondsToCeiling = withinRun
                ? hits.get(Math.max(0, (int) Math.ceil(0.5 * hits.size()) - 1))
                : ceiling.isPresent() && deficit > 0 ? ceiling.getAsLong() / deficit : null;

        return new Saturation(
                rate, model.capacity(), deficit, secondsToCeiling, withinRun, failureMode(rounds), model.cost(),
                recommendation(scenario, model));
    }

    /** The mode most rounds ended in. */
    static String failureMode(List<RunStatistics> rounds) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RunStatistics round : rounds) {
            String mode = round.ceilingSecond() >= 0
                    ? (round.blockedSeconds() > 0 ? PRODUCER_BLOCKED : DROP)
                    : round.throttled() ? INFLIGHT_EXHAUSTED : UNBOUNDED_BACKLOG;
            counts.merge(mode, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(UNBOUNDED_BACKLOG);
    }

    /** e.g. "63 consumidores ou 1 ms de processamento" for the rate offered. */
    static String recommendation(Scenario scenario, BrokerModel model) {
        double rate = scenario.getRatePerSecond();
        int needed = (int) Math.ceil(rate * scenario.getProcessingMs() / 1000.0 / TARGET_OCCUPANCY);
        int fastestMs = Math.max(1, (int) Math.floor(1000.0 * model.effectiveConsumers() / rate * TARGET_OCCUPANCY));
        String partitions = model.broker() == BrokerType.KAFKA
                ? " (e ao menos " + needed + " partições, senão os extras ficam ociosos)"
                : "";
        return String.format(
                Locale.forLanguageTag("pt-BR"),
                "Para %,.0f msg/s a ~80%% de ocupação: %d consumidores ativos%s, ou reduzir o processamento "
                        + "para ~%d ms (hoje %d consumidores a %d ms).",
                rate, needed, partitions, fastestMs, model.effectiveConsumers(), scenario.getProcessingMs());
    }
}
