import type { ScenarioFormValues } from '../../api/modules/types';

export const defaultScenario: ScenarioFormValues = {
  name: 'Novo cenário',
  broker: 'KAFKA',
  ratePerSecond: 200,
  consumers: 4,
  processingMs: 15,
  failurePct: 1,
  maxRetries: 3,
  messageSizeKb: 2,
  durationSeconds: 120,
  queueCapacity: null,
  partitions: 6,
  visibilityTimeoutSeconds: null,
  dlqEnabled: true,
  burstEnabled: false,
};
