import { useCallback, useEffect, useRef, useState } from 'react';
import { ScenarioService } from '../../api/modules/scenario.service';
import { RunService } from '../../api/modules/run.service';
import type { RunSummary, Scenario, SimulationEvent, Tick } from '../../api/modules/types';

const LIVE_SPEED = 5;

export function useRunReport(scenarioId: string) {
  const [scenario, setScenario] = useState<Scenario | null>(null);
  const [run, setRun] = useState<RunSummary | null>(null);
  const [ticks, setTicks] = useState<Tick[]>([]);
  const [events, setEvents] = useState<SimulationEvent[]>([]);
  const [running, setRunning] = useState(false);
  const [live, setLive] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const eventSourceRef = useRef<EventSource | null>(null);

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

  const closeStream = useCallback(() => {
    eventSourceRef.current?.close();
    eventSourceRef.current = null;
  }, []);

  useEffect(() => closeStream, [closeStream]);

  const runInstant = useCallback(async () => {
    closeStream();
    setError(null);
    setRunning(true);
    setLive(false);
    setTicks([]);
    setEvents([]);
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
  }, [scenarioId, closeStream]);

  const runLive = useCallback(async () => {
    closeStream();
    setError(null);
    setTicks([]);
    setEvents([]);
    setRunning(true);
    setLive(true);
    try {
      const summary = await RunService.runLive(scenarioId);
      setRun(summary);

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
  }, [scenarioId, closeStream]);

  const stopLive = useCallback(async () => {
    if (!run) return;
    try {
      await RunService.stop(run.id);
    } catch {
      // the SSE "finished" event (status STOPPED) is the source of truth either way
    }
  }, [run]);

  return { scenario, run, ticks, events, running, live, error, runInstant, runLive, stopLive };
}
