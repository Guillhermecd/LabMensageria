package com.bimd.msgsim.domain.dto;

import com.bimd.msgsim.domain.model.RunMode;
import com.bimd.msgsim.domain.model.RunStatus;
import java.time.Instant;
import java.util.UUID;

public record RunSummaryResponse(
        UUID id,
        UUID scenarioId,
        RunStatus status,
        RunMode mode,
        long seed,
        Instant startedAt,
        Instant finishedAt,
        long producedTotal,
        long deliveredTotal,
        long dlqTotal,
        long droppedTotal,
        long retriesTotal) {
}
