package com.bimd.msgsim.domain.dto;

/** Spread of one metric across the rounds of a batch. Higher is worse for every metric reported. */
public record MetricRange(double min, double median, double p95, double worst, long worstSeed) {
}
