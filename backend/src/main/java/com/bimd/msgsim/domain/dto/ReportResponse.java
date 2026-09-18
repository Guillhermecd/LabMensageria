package com.bimd.msgsim.domain.dto;

import java.util.List;

public record ReportResponse(String narrative, List<InsightResponse> insights, List<String> conclusion) {
}
