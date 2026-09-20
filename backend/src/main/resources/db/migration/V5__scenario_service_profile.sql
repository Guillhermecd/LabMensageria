ALTER TABLE scenarios
    ADD COLUMN service_profile VARCHAR(20) NOT NULL DEFAULT 'CONSTANT'
        CHECK (service_profile IN ('CONSTANT', 'EXPONENTIAL', 'HEAVY_TAIL'));
