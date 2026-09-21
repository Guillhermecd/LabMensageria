export type User = {
  id: string;
  name: string;
  email: string;
  createdAt: string;
  updatedAt: string;
};

export type Broker = 'KAFKA' | 'RABBITMQ' | 'SQS';

export type ExecutionMode = 'SIMULATED' | 'REAL';
export type ServiceProfile = 'CONSTANT' | 'EXPONENTIAL' | 'HEAVY_TAIL';

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
  retentionHours: number | null;
  retentionMb: number | null;
  highWatermarkMb: number | null;
  prefetch: number | null;
  inflightMax: number | null;
  dlqEnabled: boolean;
  burstEnabled: boolean;
  executionMode: ExecutionMode;
  serviceProfile: ServiceProfile;
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

export type Tradeoff = {
  broker: Broker;
  best: boolean;
  score: number;
  capacity: number;
  ratio: number;
  p50Ms: number;
  p99Ms: number;
  backlog: number;
  loss: number;
  lossPct: number;
  cost: number;
  pros: string[];
  cons: string[];
};

export type InsightTone = 'ok' | 'warning' | 'danger' | 'info';

export type Insight = {
  tone: InsightTone;
  text: string;
};

export type AnalyticalReport = {
  narrative: string;
  insights: Insight[];
  conclusion: string[];
};

export type RunReport = {
  scenario: Scenario;
  run: RunSummary;
  ticks: Tick[];
  events: SimulationEvent[];
};

export type MetricRange = {
  min: number;
  median: number;
  p95: number;
  worst: number;
  worstSeed: number;
};

export type BatchSummary = {
  scenarioId: string;
  rounds: number;
  masterSeed: number;
  warmupSeconds: number;
  p50Ms: MetricRange;
  p95Ms: MetricRange;
  p99Ms: MetricRange;
  peakBacklog: MetricRange;
  lossPct: MetricRange;
  rawCapacity: number;
  usefulCapacity: number;
};

export type DecisionPoints = {
  stability: number;
  latency: number;
  loss: number;
  cost: number;
  ops: number;
};

export type BrokerDecision = {
  broker: Broker;
  scoreMedian: number;
  scoreLow: number;
  scoreHigh: number;
  points: DecisionPoints;
  modelP50Ms: number;
  simP50Ms: number;
  modelP99Ms: number;
  simP99Ms: number;
  modelErrorPct: number;
  simP99WorstMs: number;
  peakBacklogMedian: number;
  lossPctMedian: number;
};

export type Decision = {
  rounds: number;
  masterSeed: number;
  stabilityWeight: number;
  latencyWeight: number;
  lossWeight: number;
  costWeight: number;
  opsWeight: number;
  brokers: BrokerDecision[];
  tie: boolean;
  leader: Broker;
  runnerUp: Broker;
  scoreGap: number;
  leaderWinRate: number;
  decidedBy: string;
  verdict: string;
};

export type ScoreWeights = {
  stability: number;
  latency: number;
  loss: number;
  cost: number;
  ops: number;
};

export type CompareResult = {
  a: RunReport;
  b: RunReport;
  comparison: string;
};


export type SuiteVariant = {
  variant: string;
  label: string;
  occupancyPct: number;
  ratePerSecond: number;
  detail: string;
  decision: Decision;
};

export type Suite = { variants: SuiteVariant[]; leaderChanges: boolean; summary: string };

export type SweepPoint = {
  occupancyPct: number;
  ratePerSecond: number;
  p99MedianMs: number;
  p99P95Ms: number;
  p99WorstMs: number;
  peakBacklogMedian: number;
};

export type Sweep = {
  rounds: number;
  masterSeed: number;
  brokers: { broker: Broker; capacity: number; points: SweepPoint[] }[];
};
