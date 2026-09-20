import { api, authStorage, BASE_URL } from './api';
import type {
  AnalyticalReport,
  BatchSummary,
  CompareResult,
  Decision,
  Suite,
  Sweep,
  RunSummary,
  ScoreWeights,
  SimulationEvent,
  Tick,
} from './types';

export const RunService = {
  runInstant(scenarioId: string, seed?: number) {
    const query = seed != null ? `?mode=INSTANT&seed=${seed}` : '?mode=INSTANT';
    return api<RunSummary>(`/scenarios/${scenarioId}/runs${query}`, { method: 'POST' });
  },
  runLive(scenarioId: string, seed?: number) {
    const query = seed != null ? `?mode=LIVE&seed=${seed}` : '?mode=LIVE';
    return api<RunSummary>(`/scenarios/${scenarioId}/runs${query}`, { method: 'POST' });
  },
  runBatch(scenarioId: string, rounds = 30) {
    return api<BatchSummary>(`/scenarios/${scenarioId}/batches?rounds=${rounds}`, {
      method: 'POST',
    });
  },
  decision(scenarioId: string, weights: ScoreWeights, rounds = 30) {
    const query = new URLSearchParams({ rounds: String(rounds) });
    (Object.keys(weights) as (keyof ScoreWeights)[]).forEach((key) =>
      query.set(key, String(weights[key])),
    );
    return api<Decision>(`/scenarios/${scenarioId}/decision?${query}`);
  },
  suite(scenarioId: string, rounds = 20) {
    return api<Suite>(`/scenarios/${scenarioId}/suite?rounds=${rounds}`);
  },
  sweep(scenarioId: string, rounds = 20) {
    return api<Sweep>(`/scenarios/${scenarioId}/sweep?rounds=${rounds}`);
  },
  stop(runId: string) {
    return api<void>(`/runs/${runId}/stop`, { method: 'POST' });
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
  getReport(runId: string) {
    return api<AnalyticalReport>(`/runs/${runId}/report`);
  },
  compare(runIdA: string, runIdB: string) {
    return api<CompareResult>(`/runs/compare?a=${runIdA}&b=${runIdB}`);
  },
  streamUrl(runId: string, speed: number) {
    const token = authStorage.getToken() ?? '';
    return `${BASE_URL}/runs/${runId}/stream?speed=${speed}&token=${encodeURIComponent(token)}`;
  },
};
