ALTER TABLE scenarios
    ADD COLUMN execution_mode VARCHAR(20) NOT NULL DEFAULT 'SIMULATED'
        CHECK (execution_mode IN ('SIMULATED', 'REAL'));
