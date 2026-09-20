package com.bimd.msgsim.service.report;

import com.bimd.msgsim.domain.dto.DecisionResponse;
import com.bimd.msgsim.domain.dto.SuiteResponse;
import com.bimd.msgsim.domain.dto.SuiteResponse.VariantResult;
import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Fills the broker x scenario matrix. The healthy column is where brokers tie; the columns where
 * something breaks are where retention, redelivery and rebalancing start to matter.
 */
@Service
@RequiredArgsConstructor
public class SuiteService {

    private final DecisionService decisionService;

    public SuiteResponse run(String ownerEmail, UUID scenarioId, ScoreWeights weights, int rounds, Long requestedSeed) {
        Scenario base = decisionService.ownedSimulatedScenario(ownerEmail, scenarioId, rounds);
        long masterSeed = DecisionService.resolveSeed(requestedSeed);

        List<VariantResult> variants = new ArrayList<>();
        for (ScenarioVariant variant : ScenarioVariant.values()) {
            DecisionResponse decision = decisionService.decide(
                    variant.apply(base), weights, rounds, masterSeed, variant.disturbance(base));
            variants.add(new VariantResult(variant.name(), variant.label(), decision));
        }
        return summarize(variants);
    }

    static SuiteResponse summarize(List<VariantResult> variants) {
        Map<BrokerType, Long> wins = new EnumMap<>(BrokerType.class);
        variants.stream()
                .filter(v -> !v.decision().tie())
                .forEach(v -> wins.merge(v.decision().leader(), 1L, Long::sum));
        boolean leaderChanges = wins.size() > 1;

        String perVariant = variants.stream()
                .map(v -> v.label() + ": " + (v.decision().tie()
                        ? "empate técnico (" + v.decision().leader() + " / " + v.decision().runnerUp() + ")"
                        : v.decision().leader()))
                .collect(Collectors.joining("; "));
        String verdict = leaderChanges
                ? "O melhor broker muda conforme o cenário: escolher só pelo caso saudável esconde isso."
                : wins.isEmpty()
                        ? "Nenhum broker se destaca em nenhum cenário testado."
                        : "O mesmo broker lidera em todos os cenários em que há diferença material.";
        return new SuiteResponse(variants, leaderChanges, verdict + " " + perVariant + ".");
    }
}
