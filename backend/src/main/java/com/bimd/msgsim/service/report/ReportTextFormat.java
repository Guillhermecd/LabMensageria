package com.bimd.msgsim.service.report;

import com.bimd.msgsim.domain.model.Scenario;
import java.util.Locale;

/** Shared formatting for the report's Portuguese prose — narrative, insights and conclusion alike. */
final class ReportTextFormat {

    private static final int DEFAULT_VISIBILITY_TIMEOUT_SECONDS = 30;

    private ReportTextFormat() {
    }

    /** Fixed regardless of server locale — the narrative text itself is always Portuguese. */
    static String format(String pattern, Object... args) {
        return String.format(Locale.forLanguageTag("pt-BR"), pattern, args);
    }

    static String fmt(double n) {
        if (n >= 1e6) return format("%.2fM", n / 1e6);
        if (n >= 1e4) return format("%.1fk", n / 1e3);
        return String.valueOf(Math.round(n));
    }

    static int visibilityTimeout(Scenario scenario) {
        return scenario.getVisibilityTimeoutSeconds() != null
                ? scenario.getVisibilityTimeoutSeconds()
                : DEFAULT_VISIBILITY_TIMEOUT_SECONDS;
    }
}
