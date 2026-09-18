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
