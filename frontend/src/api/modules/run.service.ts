import { api } from './api';
import type { RunSummary, SimulationEvent, Tick } from './types';

export const RunService = {
  runInstant(scenarioId: string, seed?: number) {
    const query = seed != null ? `?mode=INSTANT&seed=${seed}` : '?mode=INSTANT';
    return api<RunSummary>(`/scenarios/${scenarioId}/runs${query}`, { method: 'POST' });
  },
  get(runId: string) {
    return api<RunSummary>(`/runs/${runId}`);
  },
  listTicks(runId: string) {
    return api<Tick[]>(`/runs/${runId}/ticks`);
  },
  listEvents(runId: string) {
    return api<SimulationEvent[]>(`/runs/${runId}/events`);
  },
};
