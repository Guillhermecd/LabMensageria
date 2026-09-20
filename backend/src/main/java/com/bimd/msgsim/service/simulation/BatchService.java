package com.bimd.msgsim.service.simulation;

import com.bimd.msgsim.domain.dto.BatchSummaryResponse;
import com.bimd.msgsim.domain.dto.MetricRange;
import com.bimd.msgsim.domain.model.ExecutionMode;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.exception.BusinessException;
import com.bimd.msgsim.repository.ScenarioRepository;
import com.bimd.msgsim.repository.UserRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Runs the same scenario under N seeds and reports a range instead of a single point. Rounds are
 * not persisted: each seed is derived from a master seed, so any round can be replayed as a normal run.
 */
@Service
@RequiredArgsConstructor
public class BatchService {

    public static final int DEFAULT_ROUNDS = 30;
    public static final int MAX_ROUNDS = 200;
    /** Seeds are echoed to the browser as JSON numbers, which are exact only up to 2^53. */
    private static final long JS_SAFE_MASK = (1L << 53) - 1;

    private final ScenarioRepository scenarioRepository;
    private final UserRepository userRepository;
    private final SimulationEngine engine;

    public BatchSummaryResponse run(String ownerEmail, UUID scenarioId, int rounds, Long requestedSeed) {
        if (rounds < 2 || rounds > MAX_ROUNDS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "rounds must be between 2 and " + MAX_ROUNDS);
        }
        Scenario scenario = findOwnedScenario(ownerEmail, scenarioId);
        if (scenario.getExecutionMode() == ExecutionMode.REAL) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "batches are only available for simulated scenarios");
        }
        long masterSeed = requestedSeed != null ? requestedSeed : System.nanoTime() & JS_SAFE_MASK;
        return summarize(scenario, rounds, masterSeed);
    }

    public BatchSummaryResponse summarize(Scenario scenario, int rounds, long masterSeed) {
        Random seeds = new Random(masterSeed);
        List<Round> results = new ArrayList<>(rounds);
        for (int i = 0; i < rounds; i++) {
            long seed = seeds.nextLong() & JS_SAFE_MASK;
            results.add(new Round(seed, RunStatistics.of(engine.runToCompletion(scenario, seed), scenario)));
        }
        return new BatchSummaryResponse(
                scenario.getId(), rounds, masterSeed, RunStatistics.warmupSeconds(scenario.getDurationSeconds()),
                range(results, RunStatistics::p50Ms),
                range(results, RunStatistics::p95Ms),
                range(results, RunStatistics::p99Ms),
                range(results, RunStatistics::peakBacklog),
                range(results, RunStatistics::lossPct),
                median(results, RunStatistics::rawCapacity),
                median(results, RunStatistics::usefulCapacity));
    }

    private static List<Round> sortedBy(List<Round> rounds, ToDoubleFunction<RunStatistics> metric) {
        return rounds.stream()
                .sorted(Comparator.comparingDouble(r -> metric.applyAsDouble(r.stats())))
                .toList();
    }

    private static MetricRange range(List<Round> rounds, ToDoubleFunction<RunStatistics> metric) {
        List<Round> sorted = sortedBy(rounds, metric);
        Round worst = sorted.get(sorted.size() - 1);
        return new MetricRange(
                metric.applyAsDouble(sorted.get(0).stats()),
                percentile(sorted, 0.50, metric),
                percentile(sorted, 0.95, metric),
                metric.applyAsDouble(worst.stats()),
                worst.seed());
    }

    private static double median(List<Round> rounds, ToDoubleFunction<RunStatistics> metric) {
        return percentile(sortedBy(rounds, metric), 0.50, metric);
    }

    /** Nearest-rank percentile over an already sorted list. */
    private static double percentile(List<Round> sorted, double p, ToDoubleFunction<RunStatistics> metric) {
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        return metric.applyAsDouble(sorted.get(Math.max(0, index)).stats());
    }

    private Scenario findOwnedScenario(String ownerEmail, UUID scenarioId) {
        UUID ownerId = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"))
                .getId();
        return scenarioRepository.findByIdAndOwnerId(scenarioId, ownerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Scenario not found"));
    }

    private record Round(long seed, RunStatistics stats) {
    }
}
