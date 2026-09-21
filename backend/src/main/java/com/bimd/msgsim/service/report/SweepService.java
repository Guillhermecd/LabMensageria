package com.bimd.msgsim.service.report;

import com.bimd.msgsim.domain.dto.BatchSummaryResponse;
import com.bimd.msgsim.domain.dto.SweepResponse;
import com.bimd.msgsim.domain.dto.SweepResponse.BrokerSweep;
import com.bimd.msgsim.domain.dto.SweepResponse.SweepPoint;
import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.service.simulation.BatchService;
import com.bimd.msgsim.service.simulation.ScenarioVariants;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Latency versus occupancy: how p99 explodes as load approaches capacity. A single run can never
 * show this curve, and it is the most useful intuition the simulator can give.
 */
@Service
@RequiredArgsConstructor
public class SweepService {

    static final int[] OCCUPANCY_PCT = {50, 75, 90, 95};

    private final DecisionService decisionService;
    private final BatchService batchService;
    private final AnalyticalQueueModel model;

    public SweepResponse run(String ownerEmail, UUID scenarioId, int rounds, Long requestedSeed) {
        Scenario base = decisionService.ownedSimulatedScenario(ownerEmail, scenarioId, rounds);
        long masterSeed = DecisionService.resolveSeed(requestedSeed);
        return sweep(base, rounds, masterSeed);
    }

    SweepResponse sweep(Scenario base, int rounds, long masterSeed) {
        List<BrokerSweep> brokers = new ArrayList<>();
        for (BrokerType broker : BrokerType.values()) {
            Scenario clean = ScenarioVariants.withoutBurst(ScenarioVariants.withBroker(base, broker));
            double capacity = model.compute(clean, broker).capacity();
            List<SweepPoint> points = new ArrayList<>();
            for (int pct : OCCUPANCY_PCT) {
                int rate = (int) Math.max(1, Math.round(capacity * pct / 100.0));
                BatchSummaryResponse batch = batchService.summarize(
                        ScenarioVariants.withRate(clean, rate), rounds, masterSeed);
                points.add(new SweepPoint(
                        pct, rate, batch.p99Ms().median(), batch.p99Ms().p95(), batch.p99Ms().worst(),
                        batch.peakBacklog().median()));
            }
            brokers.add(new BrokerSweep(broker, capacity, points));
        }
        return new SweepResponse(rounds, masterSeed, brokers);
    }
}
