ALTER TABLE scenarios
    ADD COLUMN retention_hours INTEGER,
    ADD COLUMN retention_mb INTEGER,
    ADD COLUMN high_watermark_mb INTEGER,
    ADD COLUMN prefetch INTEGER,
    ADD COLUMN inflight_max INTEGER;
