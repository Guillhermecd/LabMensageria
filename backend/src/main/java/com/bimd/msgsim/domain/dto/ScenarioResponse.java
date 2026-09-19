package com.bimd.msgsim.domain.dto;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.ExecutionMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ScenarioResponse(
        UUID id,
        String name,
        BrokerType broker,
        int ratePerSecond,
        int consumers,
        int processingMs,
        BigDecimal failurePct,
        int maxRetries,
        int messageSizeKb,
        int durationSeconds,
        Integer queueCapacity,
        Integer partitions,
        Integer visibilityTimeoutSeconds,
        boolean dlqEnabled,
        boolean burstEnabled,
        ExecutionMode executionMode,
        Instant createdAt,
        Instant updatedAt) {
}
