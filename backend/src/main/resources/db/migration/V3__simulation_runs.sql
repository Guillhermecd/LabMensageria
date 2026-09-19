CREATE TABLE simulation_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scenario_id UUID NOT NULL REFERENCES scenarios (id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'STOPPED', 'COMPLETED')),
    mode VARCHAR(20) NOT NULL CHECK (mode IN ('LIVE', 'INSTANT')),
    seed BIGINT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    produced_total BIGINT NOT NULL DEFAULT 0,
    delivered_total BIGINT NOT NULL DEFAULT 0,
    dlq_total BIGINT NOT NULL DEFAULT 0,
    dropped_total BIGINT NOT NULL DEFAULT 0,
    retries_total BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE simulation_ticks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES simulation_runs (id) ON DELETE CASCADE,
    second INTEGER NOT NULL,
    produced INTEGER NOT NULL,
    consumed INTEGER NOT NULL,
    failed INTEGER NOT NULL,
    dropped INTEGER NOT NULL,
    backlog INTEGER NOT NULL,
    utilization DOUBLE PRECISION NOT NULL,
    p50_ms INTEGER NOT NULL,
    p95_ms INTEGER NOT NULL,
    p99_ms INTEGER NOT NULL,
    capacity DOUBLE PRECISION NOT NULL
);

CREATE INDEX idx_simulation_ticks_run_second ON simulation_ticks (run_id, second);

CREATE TABLE simulation_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES simulation_runs (id) ON DELETE CASCADE,
    second INTEGER NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN (
        'SATURATION', 'QUEUE_FULL', 'FIRST_DLQ', 'LAG',
        'BURST_START', 'BURST_END', 'RECOVERED', 'FINISHED'
    )),
    message VARCHAR(500) NOT NULL
);

CREATE INDEX idx_simulation_events_run_id ON simulation_events (run_id);
