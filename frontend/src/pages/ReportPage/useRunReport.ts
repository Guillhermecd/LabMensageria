import { useCallback, useEffect, useRef, useState } from 'react';
import { ScenarioService } from '../../api/modules/scenario.service';
import { RunService } from '../../api/modules/run.service';
import type {
  AnalyticalReport,
  RunSummary,
  Scenario,
  SimulationEvent,
  Tick,
  Tradeoff,
} from '../../api/modules/types';

const LIVE_SPEED = 5;

export function useRunReport(scenarioId: string) {
  const [scenario, setScenario] = useState<Scenario | null>(null);
  const [tradeoffs, setTradeoffs] = useState<Tradeoff[]>([]);
  const [history, setHistory] = useState<RunSummary[]>([]);
  const [run, setRun] = useState<RunSummary | null>(null);
  const [ticks, setTicks] = useState<Tick[]>([]);
  const [events, setEvents] = useState<SimulationEvent[]>([]);
  const [report, setReport] = useState<AnalyticalReport | null>(null);
  const [running, setRunning] = useState(false);
  const [live, setLive] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const eventSourceRef = useRef<EventSource | null>(null);

  const refreshHistory = useCallback(async () => {
    try {
      setHistory(await ScenarioService.runHistory(scenarioId));
    } catch {
      // history is a convenience list — a failure here shouldn't block the report itself
    }
  }, [scenarioId]);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      ScenarioService.get(scenarioId),
      ScenarioService.tradeoffs(scenarioId),
      ScenarioService.runHistory(scenarioId),
    ])
      .then(([scenarioData, tradeoffData, historyData]) => {
        if (cancelled) return;
        setScenario(scenarioData);
        setTradeoffs(tradeoffData);
        setHistory(historyData);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Falha ao carregar cenário');
      });
    return () => {
      cancelled = true;
    };
  }, [scenarioId]);

  const closeStream = useCallback(() => {
    eventSourceRef.current?.close();
    eventSourceRef.current = null;
  }, []);

  useEffect(() => closeStream, [closeStream]);

  const loadReport = useCallback(async (runId: string) => {
    try {
      setReport(await RunService.getReport(runId));
    } catch {
      // the tick/KPI data already rendered is more important than the narrative text
    }
  }, []);

  const runInstant = useCallback(async (seed?: number) => {
    closeStream();
    setError(null);
    setRunning(true);
    setLive(false);
    setTicks([]);
    setEvents([]);
    setReport(null);
    try {
      const summary = await RunService.runInstant(scenarioId, seed);
      const [tickList, eventList] = await Promise.all([
        RunService.listTicks(summary.id),
        RunService.listEvents(summary.id),
      ]);
      setRun(summary);
      setTicks(tickList);
      setEvents(eventList);
      await loadReport(summary.id);
      await refreshHistory();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao rodar a simulação');
    } finally {
      setRunning(false);
    }
  }, [scenarioId, closeStream, loadReport, refreshHistory]);

  const runLive = useCallback(async () => {
    closeStream();
    setError(null);
    setTicks([]);
    setEvents([]);
    setReport(null);
    setRunning(true);
    setLive(true);
    try {
      const summary = await RunService.runLive(scenarioId);
      setRun(summary);
      await refreshHistory();

      const source = new EventSource(RunService.streamUrl(summary.id, LIVE_SPEED));
      eventSourceRef.current = source;

      source.addEventListener('tick', (message) => {
        const tick = JSON.parse((message as MessageEvent).data) as Tick;
        setTicks((prev) => [...prev, tick]);
      });
      source.addEventListener('event', (message) => {
        const event = JSON.parse((message as MessageEvent).data) as SimulationEvent;
        setEvents((prev) => [...prev, event]);
      });
      source.addEventListener('finished', (message) => {
        const finalSummary = JSON.parse((message as MessageEvent).data) as RunSummary;
        setRun(finalSummary);
        setRunning(false);
        closeStream();
        loadReport(finalSummary.id);
        refreshHistory();
      });
      source.onerror = () => {
        setError('Conexão com a simulação ao vivo foi perdida.');
        setRunning(false);
        closeStream();
      };
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao iniciar simulação ao vivo');
      setRunning(false);
    }
  }, [scenarioId, closeStream, loadReport, refreshHistory]);

  const stopLive = useCallback(async () => {
    if (!run) return;
    try {
      await RunService.stop(run.id);
    } catch {
      // the SSE "finished" event (status STOPPED) is the source of truth either way
    }
  }, [run]);

  const reopen = useCallback(
    async (runId: string) => {
      closeStream();
      setError(null);
      setRunning(false);
      setLive(false);
      setReport(null);
      try {
        const [summary, tickList, eventList] = await Promise.all([
          RunService.get(runId),
          RunService.listTicks(runId),
          RunService.listEvents(runId),
        ]);
        setRun(summary);
        setTicks(tickList);
        setEvents(eventList);
        await loadReport(runId);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Falha ao reabrir execução');
      }
    },
    [closeStream, loadReport],
  );

  return {
    scenario,
    tradeoffs,
    history,
    run,
    ticks,
    events,
    report,
    running,
    live,
    error,
    runInstant,
    runLive,
    stopLive,
    reopen,
  };
}
