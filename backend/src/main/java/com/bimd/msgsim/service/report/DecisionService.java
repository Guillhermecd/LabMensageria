package com.bimd.msgsim.service.report;

import com.bimd.msgsim.domain.dto.DecisionResponse;
import com.bimd.msgsim.domain.dto.DecisionResponse.BrokerDecision;
import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.ExecutionMode;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.exception.BusinessException;
import com.bimd.msgsim.repository.ScenarioRepository;
import com.bimd.msgsim.repository.UserRepository;
import com.bimd.msgsim.service.simulation.BatchService;
import com.bimd.msgsim.service.simulation.Disturbance;
import com.bimd.msgsim.service.simulation.RunStatistics;
import com.bimd.msgsim.service.simulation.ScenarioVariants;
import com.bimd.msgsim.service.simulation.SimulationEngine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Compares brokers on simulated rounds instead of one analytical point. Every round uses the same
 * seed for all three brokers (paired), so the leader's edge is measured round by round. A leader
 * is only declared when the gap is both material (in points) and consistent (win rate); otherwise
 * the honest answer is a technical tie.
 */
@Service
@RequiredArgsConstructor
public class DecisionService {

    /** Below this many points the difference is inside what weights and heuristics can explain. */
    static final double MIN_MEANINGFUL_GAP = 5.0;
    /** The leader must beat the runner-up in at least this share of paired rounds. */
    static final double MIN_WIN_RATE = 0.9;

    private final ScenarioRepository scenarioRepository;
    private final UserRepository userRepository;
    private final SimulationEngine engine;
    private final AnalyticalQueueModel model;

    public DecisionResponse decide(
            String ownerEmail, UUID scenarioId, ScoreWeights weights, int rounds, Long requestedSeed) {
        Scenario scenario = ownedSimulatedScenario(ownerEmail, scenarioId, rounds);
        return decide(scenario, weights, rounds, resolveSeed(requestedSeed));
    }

    /** Loads the scenario for what-if analysis: owned by the caller, simulated, with a sane round count. */
    public Scenario ownedSimulatedScenario(String ownerEmail, UUID scenarioId, int rounds) {
        if (rounds < 2 || rounds > BatchService.MAX_ROUNDS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "rounds must be between 2 and " + BatchService.MAX_ROUNDS);
        }
        Scenario scenario = findOwnedScenario(ownerEmail, scenarioId);
        if (scenario.getExecutionMode() == ExecutionMode.REAL) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "this analysis is only available for simulated scenarios");
        }
        return scenario;
    }

    /** Seeds are echoed to the browser as JSON numbers, which are exact only up to 2^53. */
    public static long resolveSeed(Long requestedSeed) {
        return requestedSeed != null ? requestedSeed : System.nanoTime() & ((1L << 53) - 1);
    }

    DecisionResponse decide(Scenario scenario, ScoreWeights weights, int rounds, long masterSeed) {
        return decide(scenario, weights, rounds, masterSeed, Disturbance.NONE);
    }

    public DecisionResponse decide(
            Scenario scenario, ScoreWeights weights, int rounds, long masterSeed, Disturbance disturbance) {
        BrokerType[] brokers = BrokerType.values();
        Map<BrokerType, BrokerModel> models = new EnumMap<>(BrokerType.class);
        Map<BrokerType, Scenario> variants = new EnumMap<>(BrokerType.class);
        Map<BrokerType, List<RunStatistics>> stats = new EnumMap<>(BrokerType.class);
        for (BrokerType broker : brokers) {
            Scenario variant = ScenarioVariants.withBroker(scenario, broker);
            variants.put(broker, variant);
            models.put(broker, model.compute(variant, broker));
            stats.put(broker, new ArrayList<>());
        }

        Random seeds = new Random(masterSeed);
        Map<BrokerType, List<ScoreCalculator.Points>> perRound = new EnumMap<>(BrokerType.class);
        for (BrokerType broker : brokers) {
            perRound.put(broker, new ArrayList<>());
        }
        for (int i = 0; i < rounds; i++) {
            long seed = seeds.nextLong() & ((1L << 53) - 1);
            List<ScoreCalculator.Inputs> inputs = new ArrayList<>();
            for (BrokerType broker : brokers) {
                Scenario variant = variants.get(broker);
                RunStatistics s = RunStatistics.of(engine.runToCompletion(variant, seed, disturbance), variant);
                stats.get(broker).add(s);
                BrokerModel m = models.get(broker);
                inputs.add(new ScoreCalculator.Inputs(
                        broker, roundStability(m, s, variant), s.p99Ms(), s.lossPct(),
                        m.cost(), m.opsScore()));
            }
            ScoreCalculator.score(inputs, weights).forEach((broker, points) -> perRound.get(broker).add(points));
        }

        List<BrokerType> ranked = Arrays.stream(brokers)
                .sorted(Comparator.comparingDouble((BrokerType b) -> median(totals(perRound.get(b)))).reversed())
                .toList();
        BrokerType leader = ranked.get(0);
        BrokerType runnerUp = ranked.get(1);
        List<ScoreCalculator.Points> leaderPerRound = perRound.get(leader);
        List<ScoreCalculator.Points> runnerPerRound = perRound.get(runnerUp);
        // a leader is only real if it is ahead on the technical criteria; cost and operations break ties
        long wins = 0;
        List<Double> technicalDiffs = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            double diff = technical(leaderPerRound.get(i)) - technical(runnerPerRound.get(i));
            technicalDiffs.add(diff);
            if (diff > 0) wins++;
        }
        double winRate = wins / (double) rounds;
        double gap = median(totals(leaderPerRound)) - median(totals(runnerPerRound));
        double technicalGap = median(technicalDiffs);
        boolean tie = Math.abs(technicalGap) < MIN_MEANINGFUL_GAP || (technicalGap > 0 && winRate < MIN_WIN_RATE);

        DecisionResponse.Points leaderPoints = meanPoints(perRound.get(leader));
        DecisionResponse.Points runnerPoints = meanPoints(perRound.get(runnerUp));
        String decidedBy = decidingCriterion(leaderPoints, runnerPoints);

        List<BrokerDecision> rows = new ArrayList<>();
        for (BrokerType broker : brokers) {
            List<Double> totals = totals(perRound.get(broker));
            List<RunStatistics> s = stats.get(broker);
            BrokerModel m = models.get(broker);
            double simP50 = median(s.stream().map(RunStatistics::p50Ms).toList());
            double simP99 = median(s.stream().map(RunStatistics::p99Ms).toList());
            double simP99Worst = s.stream().mapToDouble(RunStatistics::p99Ms).max().orElse(0);
            double peakBacklog = median(s.stream().map(RunStatistics::peakBacklog).toList());
            double lossPct = median(s.stream().map(RunStatistics::lossPct).toList());
            rows.add(new BrokerDecision(
                    broker, median(totals), percentile(totals, 0.10), percentile(totals, 0.90),
                    meanPoints(perRound.get(broker)), m.p50Ms(), simP50, m.p99Ms(), simP99,
                    (Math.abs(relativeError(m.p50Ms(), simP50)) + Math.abs(relativeError(m.p99Ms(), simP99))) / 2,
                    simP99Worst, peakBacklog, lossPct));
        }

        ScoreWeights w = weights.normalized();
        return new DecisionResponse(
                rounds, masterSeed, w.stability(), w.latency(), w.loss(), w.cost(), w.ops(), rows,
                tie, leader, runnerUp, gap, winRate, decidedBy,
                verdict(tie, leader, runnerUp, gap, technicalGap, winRate, decidedBy));
    }

    private static String verdict(
            boolean tie, BrokerType leader, BrokerType runnerUp, double gap, double technicalGap, double winRate,
            String decidedBy) {
        Locale pt = Locale.forLanguageTag("pt-BR");
        String caveat = "custo".equals(decidedBy)
                ? " O custo é o da rodada simulada: projete para o volume real antes de decidir."
                : "";
if (tie) { return String.format(pt, "Empate técnico entre %s e %s: nos critérios técnicos (estabilidade, latência, perda) a diferença é de " + "%.1f pontos, dentro do ruído. O desempate seria %s (%.1f pontos a favor de %s no total) — " + "a decisão aqui não é técnica.", leader, runnerUp, Math.abs(technicalGap), decidedBy, gap, leader) + caveat; }
        return String.format(pt,
                "%s lidera %s por %.1f pontos e vence em %.0f%% das rodadas. Decidiu: %s.",
                leader, runnerUp, gap, winRate * 100, decidedBy) + caveat;
    }

    private static String decidingCriterion(DecisionResponse.Points a, DecisionResponse.Points b) {
        String[] names = {"estabilidade", "latência", "perda de mensagens", "custo", "simplicidade operacional"};
        double[] diffs = {
                a.stability() - b.stability(), a.latency() - b.latency(), a.loss() - b.loss(),
                a.cost() - b.cost(), a.ops() - b.ops()};
        int best = 0;
        for (int i = 1; i < diffs.length; i++) {
            if (Math.abs(diffs[i]) > Math.abs(diffs[best])) best = i;
        }
        return names[best];
    }

    /** Analytical stability, halved when the backlog was not drained by the end of the round (no recovery). */
    private static double roundStability(BrokerModel m, RunStatistics s, Scenario scenario) {
        double base = ScoreCalculator.stability(m.stable(), m.ratio());
        return s.endBacklog() <= scenario.getRatePerSecond() ? base : base * 0.5;
    }

    /** Points from the criteria that describe how the system behaves; cost and operations are the tiebreakers. */
    private static double technical(ScoreCalculator.Points p) {
        return p.stability() + p.latency() + p.loss();
    }

    private static double relativeError(double model, double sim) {
        return sim > 0 ? (model - sim) / sim * 100 : 0;
    }

    private static List<Double> totals(List<ScoreCalculator.Points> points) {
        return points.stream().map(ScoreCalculator.Points::total).toList();
    }

    private static DecisionResponse.Points meanPoints(List<ScoreCalculator.Points> points) {
        return new DecisionResponse.Points(
                points.stream().mapToDouble(ScoreCalculator.Points::stability).average().orElse(0),
                points.stream().mapToDouble(ScoreCalculator.Points::latency).average().orElse(0),
                points.stream().mapToDouble(ScoreCalculator.Points::loss).average().orElse(0),
                points.stream().mapToDouble(ScoreCalculator.Points::cost).average().orElse(0),
                points.stream().mapToDouble(ScoreCalculator.Points::ops).average().orElse(0));
    }

    private static double median(List<Double> values) {
        return percentile(values, 0.50);
    }

    /** Nearest-rank percentile. */
    private static double percentile(List<Double> values, double p) {
        List<Double> sorted = values.stream().sorted().toList();
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }


    private Scenario findOwnedScenario(String ownerEmail, UUID scenarioId) {
        UUID ownerId = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"))
                .getId();
        return scenarioRepository.findByIdAndOwnerId(scenarioId, ownerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Scenario not found"));
    }
}
