package com.bimd.msgsim.service.scenario;

import com.bimd.msgsim.domain.dto.ScenarioRequest;
import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.ExecutionMode;
import com.bimd.msgsim.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Broker-specific fields live in one table (nullable columns); each broker has its own rules. */
@Component
public class ScenarioValidator {

    /** Hard ceilings for execution=REAL — a runaway LoadGenerator publishing to a real
     * broker can peg this machine's CPU/memory in a way a simulated tick never can. */
    static final int MAX_REAL_RATE_PER_SECOND = 500;
    static final int MAX_REAL_DURATION_SECONDS = 120;

    public void validate(ScenarioRequest request) {
        switch (request.broker()) {
            case KAFKA -> requirePositive(request.partitions(), "partitions", BrokerType.KAFKA);
            case RABBITMQ -> requireNonNegative(request.queueCapacity(), "queueCapacity", BrokerType.RABBITMQ);
            case SQS -> requirePositive(request.visibilityTimeoutSeconds(), "visibilityTimeoutSeconds", BrokerType.SQS);
        }
        optionalPositive(request.retentionHours(), "retentionHours");
        optionalPositive(request.retentionMb(), "retentionMb");
        optionalPositive(request.highWatermarkMb(), "highWatermarkMb");
        optionalPositive(request.prefetch(), "prefetch");
        optionalPositive(request.inflightMax(), "inflightMax");
        if (request.executionMode() == ExecutionMode.REAL) {
            validateRealExecution(request);
        }
    }

    /** Broker-specific tuning is optional: null falls back to the broker's default. */
    private void optionalPositive(Integer value, String field) {
        if (value != null && value < 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, field + " must be >= 1 when informed");
        }
    }

    private void validateRealExecution(ScenarioRequest request) {
        if (request.broker() != BrokerType.RABBITMQ) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "execution=REAL is only implemented for RabbitMQ so far");
        }
        if (request.ratePerSecond() > MAX_REAL_RATE_PER_SECOND) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "ratePerSecond must be <= " + MAX_REAL_RATE_PER_SECOND + " when execution=REAL");
        }
        if (request.durationSeconds() > MAX_REAL_DURATION_SECONDS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "durationSeconds must be <= " + MAX_REAL_DURATION_SECONDS + " when execution=REAL");
        }
    }

    private void requirePositive(Integer value, String field, BrokerType broker) {
        if (value == null || value < 1) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, field + " must be >= 1 for broker " + broker);
        }
    }

    private void requireNonNegative(Integer value, String field, BrokerType broker) {
        if (value == null || value < 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, field + " must be >= 0 for broker " + broker);
        }
    }
}
