package com.bimd.msgsim.service.scenario;

import com.bimd.msgsim.domain.dto.ScenarioRequest;
import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Broker-specific fields live in one table (nullable columns); each broker has its own rules. */
@Component
public class ScenarioValidator {

    public void validate(ScenarioRequest request) {
        switch (request.broker()) {
            case KAFKA -> requirePositive(request.partitions(), "partitions", BrokerType.KAFKA);
            case RABBITMQ -> requireNonNegative(request.queueCapacity(), "queueCapacity", BrokerType.RABBITMQ);
            case SQS -> requirePositive(request.visibilityTimeoutSeconds(), "visibilityTimeoutSeconds", BrokerType.SQS);
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
