export type User = {
  id: string;
  name: string;
  email: string;
  createdAt: string;
  updatedAt: string;
};

export type Broker = 'KAFKA' | 'RABBITMQ' | 'SQS';

export type Scenario = {
  id: string;
  name: string;
  broker: Broker;
  ratePerSecond: number;
  consumers: number;
  processingMs: number;
  failurePct: number;
  maxRetries: number;
  messageSizeKb: number;
  durationSeconds: number;
  queueCapacity: number | null;
  partitions: number | null;
  visibilityTimeoutSeconds: number | null;
  dlqEnabled: boolean;
  burstEnabled: boolean;
  createdAt: string;
  updatedAt: string;
};

export type ScenarioFormValues = Omit<Scenario, 'id' | 'createdAt' | 'updatedAt'>;

export type RunStatus = 'RUNNING' | 'STOPPED' | 'COMPLETED';
export type RunMode = 'LIVE' | 'INSTANT';

export type RunSummary = {
  id: string;
  scenarioId: string;
  status: RunStatus;
  mode: RunMode;
  seed: number;
  startedAt: string;
  finishedAt: string | null;
  producedTotal: number;
  deliveredTotal: number;
  dlqTotal: number;
  droppedTotal: number;
  retriesTotal: number;
};

export type Tick = {
  second: number;
  produced: number;
  consumed: number;
  failed: number;
  dropped: number;
  backlog: number;
  utilization: number;
  p50Ms: number;
  p95Ms: number;
  p99Ms: number;
  capacity: number;
};

export type EventType =
  | 'SATURATION'
  | 'QUEUE_FULL'
  | 'FIRST_DLQ'
  | 'LAG'
  | 'BURST_START'
  | 'BURST_END'
  | 'RECOVERED'
  | 'FINISHED';

export type SimulationEvent = {
  second: number;
  type: EventType;
  message: string;
};
