package com.bimd.msgsim.domain.dto;

import java.util.List;

public record RunReportResponse(
        ScenarioResponse scenario,
        RunSummaryResponse run,
        List<TickResponse> ticks,
        List<EventResponse> events) {
}
