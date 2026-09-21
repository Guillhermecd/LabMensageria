package com.bimd.msgsim.domain.dto;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.ExecutionMode;
import com.bimd.msgsim.domain.model.ServiceProfile;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ScenarioRequest(
        @NotBlank String name,
        @NotNull BrokerType broker,
        @Min(1) int ratePerSecond,
        @Min(1) int consumers,
        @Min(1) int processingMs,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal failurePct,
        @Min(0) int maxRetries,
        @Min(1) int messageSizeKb,
        @Min(1) int durationSeconds,
        Integer queueCapacity,
        Integer partitions,
        Integer visibilityTimeoutSeconds,
        Integer retentionHours,
        Integer retentionMb,
        Integer highWatermarkMb,
        Integer prefetch,
        Integer inflightMax,
        boolean dlqEnabled,
        boolean burstEnabled,
        @NotNull ExecutionMode executionMode,
        @NotNull ServiceProfile serviceProfile) {
}
