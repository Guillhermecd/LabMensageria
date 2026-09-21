import type { Broker, BrokerDecision, FailureMode, Saturation } from '../../api/modules/types';

export const BROKER_LABEL: Record<Broker, string> = {
  KAFKA: 'Kafka',
  RABBITMQ: 'RabbitMQ',
  SQS: 'SQS',
};

const FAILURE_MODE_LABEL: Record<FailureMode, string> = {
  DROP: 'descarte de mensagens',
  PRODUCER_BLOCKED: 'produtor bloqueado',
  INFLIGHT_EXHAUSTED: 'in-flight esgotado',
  UNBOUNDED_BACKLOG: 'backlog cresce sem teto',
};

// beyond this error the analytical estimate and the simulation disagree enough to distrust the model
export const MODEL_ERROR_WARN_PCT = 25;

export function failureModeLabel(mode: FailureMode) {
  return FAILURE_MODE_LABEL[mode];
}

export function ceilingText(s: Saturation) {
  if (s.secondsToCeiling == null) return 'sem teto prático (retenção de 4 dias)';
  const seconds = Math.round(s.secondsToCeiling);
  return s.ceilingWithinRun ? `${seconds} s` : `~${seconds} s (além da simulação)`;
}

export function money(value: number) {
  return `$${value.toFixed(value < 1 ? 3 : 2)}`;
}

/** Brokers tied with this one, by name; empty when it stands apart. */
export function tiedNames(row: BrokerDecision) {
  return row.tiedWith.map((b) => BROKER_LABEL[b]).join(', ');
}
