import { useCallback, useEffect, useState } from 'react';
import { ScenarioService } from '../../api/modules/scenario.service';
import { RunService } from '../../api/modules/run.service';
import type { CompareResult, Scenario } from '../../api/modules/types';

export function useCompare() {
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [scenarioIdA, setScenarioIdA] = useState<string | null>(null);
  const [scenarioIdB, setScenarioIdB] = useState<string | null>(null);
  const [result, setResult] = useState<CompareResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    ScenarioService.list()
      .then((list) => {
        if (cancelled) return;
        setScenarios(list);
        setScenarioIdA((current) => current ?? list[0]?.id ?? null);
        setScenarioIdB((current) => current ?? list[1]?.id ?? list[0]?.id ?? null);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Falha ao carregar cenários');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const compare = useCallback(async () => {
    if (!scenarioIdA || !scenarioIdB) return;
    setError(null);
    setLoading(true);
    setResult(null);
    try {
      const [runA, runB] = await Promise.all([
        RunService.runInstant(scenarioIdA),
        RunService.runInstant(scenarioIdB),
      ]);
      setResult(await RunService.compare(runA.id, runB.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao comparar os cenários');
    } finally {
      setLoading(false);
    }
  }, [scenarioIdA, scenarioIdB]);

  return {
    scenarios,
    scenarioIdA,
    scenarioIdB,
    setScenarioIdA,
    setScenarioIdB,
    result,
    loading,
    error,
    compare,
  };
}
