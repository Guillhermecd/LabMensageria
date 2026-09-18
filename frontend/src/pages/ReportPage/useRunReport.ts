import { useCallback, useEffect, useState } from 'react';
import { ScenarioService } from '../../api/modules/scenario.service';
import { RunService } from '../../api/modules/run.service';
import type { RunSummary, Scenario, SimulationEvent, Tick } from '../../api/modules/types';

export function useRunReport(scenarioId: string) {
  const [scenario, setScenario] = useState<Scenario | null>(null);
  const [run, setRun] = useState<RunSummary | null>(null);
  const [ticks, setTicks] = useState<Tick[]>([]);
  const [events, setEvents] = useState<SimulationEvent[]>([]);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    ScenarioService.get(scenarioId)
      .then((data) => {
        if (!cancelled) setScenario(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Falha ao carregar cenário');
      });
    return () => {
      cancelled = true;
    };
  }, [scenarioId]);

  const runInstant = useCallback(async () => {
    setError(null);
    setRunning(true);
    try {
      const summary = await RunService.runInstant(scenarioId);
      const [tickList, eventList] = await Promise.all([
        RunService.listTicks(summary.id),
        RunService.listEvents(summary.id),
      ]);
      setRun(summary);
      setTicks(tickList);
      setEvents(eventList);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao rodar a simulação');
    } finally {
      setRunning(false);
    }
  }, [scenarioId]);

  return { scenario, run, ticks, events, running, error, runInstant };
}
