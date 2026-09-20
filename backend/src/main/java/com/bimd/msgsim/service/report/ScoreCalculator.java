package com.bimd.msgsim.service.report;

import com.bimd.msgsim.domain.model.BrokerType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The single place where "score" is defined. Latency and cost are scored as a ratio to the best
 * broker (best/value), not min-max: min-max stretches a 3 ms or $0.003 gap between brokers to the
 * full 0..1 range and turns noise into a winner. With ratios a 3% difference costs 3% of the weight.
 */
public final class ScoreCalculator {

    private static final double MAX_ACCEPTABLE_LOSS_PCT = 5.0;

    private ScoreCalculator() {
    }

    /** One broker's raw inputs; stability is 0..1 and ops is 0..1 (higher is better for both). */
    public record Inputs(BrokerType broker, double stability, double p99Ms, double lossPct, double cost, double ops) {
    }

    /** Points contributed by each criterion (they sum to {@link #total()}, 0..100). */
    public record Points(double stability, double latency, double loss, double cost, double ops) {
        public double total() {
            return stability + latency + loss + cost + ops;
        }
    }

    public static Map<BrokerType, Points> score(List<Inputs> inputs, ScoreWeights rawWeights) {
        ScoreWeights w = rawWeights.normalized();
        double bestP99 = inputs.stream().mapToDouble(Inputs::p99Ms).min().orElse(0);
        double bestCost = inputs.stream().mapToDouble(Inputs::cost).min().orElse(0);
        Map<BrokerType, Points> result = new EnumMap<>(BrokerType.class);
        for (Inputs in : inputs) {
            double latency = ratioToBest(bestP99, in.p99Ms());
            double loss = 1 - Math.min(1, in.lossPct() / MAX_ACCEPTABLE_LOSS_PCT);
            double cost = ratioToBest(bestCost, in.cost());
            result.put(in.broker(), new Points(
                    100 * w.stability() * in.stability(),
                    100 * w.latency() * latency,
                    100 * w.loss() * loss,
                    100 * w.cost() * cost,
                    100 * w.ops() * in.ops()));
        }
        return result;
    }

    private static double ratioToBest(double best, double value) {
        return value <= 0 || value <= best ? 1 : best / value;
    }

    public static double stability(boolean stable, double ratio) {
        return stable ? 1 - Math.min(1, ratio) * 0.5 : 0;
    }
}
