package com.bimd.msgsim.service.simulation;

/** Nearest-rank percentile over an ascending-sorted range. */
final class Percentile {

    private Percentile() {
    }

    static double of(double[] sorted, int length, double p) {
        if (length == 0) {
            return 0;
        }
        int rank = (int) Math.ceil(p * length);
        return sorted[Math.min(length - 1, Math.max(0, rank - 1))];
    }
}
