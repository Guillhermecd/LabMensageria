CREATE TABLE scenarios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users (id),
    name VARCHAR(255) NOT NULL,
    broker VARCHAR(20) NOT NULL CHECK (broker IN ('KAFKA', 'RABBITMQ', 'SQS')),
    rate_per_second INTEGER NOT NULL,
    consumers INTEGER NOT NULL,
    processing_ms INTEGER NOT NULL,
    failure_pct NUMERIC(5, 2) NOT NULL,
    max_retries INTEGER NOT NULL,
    message_size_kb INTEGER NOT NULL,
    duration_seconds INTEGER NOT NULL,
    queue_capacity INTEGER,
    partitions INTEGER,
    visibility_timeout_seconds INTEGER,
    dlq_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    burst_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_scenarios_owner_id ON scenarios (owner_id);
