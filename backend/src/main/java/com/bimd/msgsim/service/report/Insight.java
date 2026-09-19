package com.bimd.msgsim.service.report;

/** {@code tone} matches the frontend's semantic tokens: ok, warning, danger or info. */
public record Insight(String tone, String text) {
}
