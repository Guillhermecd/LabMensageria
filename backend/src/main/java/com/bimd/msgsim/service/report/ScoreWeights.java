package com.bimd.msgsim.service.report;

import com.bimd.msgsim.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * How much each criterion counts in the broker score. Values are relative: they are rescaled to
 * sum to 1, so 35/25/20/10/10 and 0.35/0.25/0.20/0.10/0.10 mean the same thing.
 */
public record ScoreWeights(double stability, double latency, double loss, double cost, double ops) {

    public static final ScoreWeights DEFAULT = new ScoreWeights(0.35, 0.25, 0.20, 0.10, 0.10);

    public ScoreWeights {
        if (stability < 0 || latency < 0 || loss < 0 || cost < 0 || ops < 0
                || stability + latency + loss + cost + ops <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "weights must be non-negative and not all zero");
        }
    }

    public ScoreWeights normalized() {
        double sum = stability + latency + loss + cost + ops;
        return new ScoreWeights(stability / sum, latency / sum, loss / sum, cost / sum, ops / sum);
    }
}
