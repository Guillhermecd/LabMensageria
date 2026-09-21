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

/** Every multi-round analysis runs this many seeds: a single run is never shown as a result. */
export const DEFAULT_ROUNDS = 25;

function roundsQuery(rounds: number, seed?: number) {
  const query = new URLSearchParams({ rounds: String(rounds) });
  if (seed != null) query.set('seed', String(seed));
  return query;
}

export const RunService = {
  runInstant(scenarioId: string, seed?: number) {
    const query = seed != null ? `?mode=INSTANT&seed=${seed}` : '?mode=INSTANT';
    return api<RunSummary>(`/scenarios/${scenarioId}/runs${query}`, { method: 'POST' });
  },
  runLive(scenarioId: string, seed?: number) {
    const query = seed != null ? `?mode=LIVE&seed=${seed}` : '?mode=LIVE';
    return api<RunSummary>(`/scenarios/${scenarioId}/runs${query}`, { method: 'POST' });
  },
  runBatch(scenarioId: string, rounds = DEFAULT_ROUNDS, seed?: number) {
    return api<BatchSummary>(`/scenarios/${scenarioId}/batches?${roundsQuery(rounds, seed)}`, {
      method: 'POST',
    });
  },
  decision(scenarioId: string, weights: ScoreWeights, rounds = DEFAULT_ROUNDS, seed?: number) {
    const query = roundsQuery(rounds, seed);
    (Object.keys(weights) as (keyof ScoreWeights)[]).forEach((key) =>
      query.set(key, String(weights[key])),
    );
    return api<Decision>(`/scenarios/${scenarioId}/decision?${query}`);
  },
  suite(scenarioId: string, rounds = DEFAULT_ROUNDS, seed?: number) {
    return api<Suite>(`/scenarios/${scenarioId}/suite?${roundsQuery(rounds, seed)}`);
  },
  sweep(scenarioId: string, rounds = DEFAULT_ROUNDS, seed?: number) {
    return api<Sweep>(`/scenarios/${scenarioId}/sweep?${roundsQuery(rounds, seed)}`);
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
