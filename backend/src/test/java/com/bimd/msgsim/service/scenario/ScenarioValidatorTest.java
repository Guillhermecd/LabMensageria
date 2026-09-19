package com.bimd.msgsim.service.scenario;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bimd.msgsim.domain.dto.ScenarioRequest;
import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.exception.BusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ScenarioValidatorTest {

    private final ScenarioValidator validator = new ScenarioValidator();

    @Test
    void should_throwBusinessException_when_kafkaHasNoPartitions() {
        ScenarioRequest request = request(BrokerType.KAFKA, null, null, null);

        assertThatThrownBy(() -> validator.validate(request)).isInstanceOf(BusinessException.class);
    }

    @Test
    void should_pass_when_kafkaHasAtLeastOnePartition() {
        ScenarioRequest request = request(BrokerType.KAFKA, 6, null, null);

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void should_pass_when_rabbitmqQueueCapacityIsZero() {
        // 0 means "unlimited" for RabbitMQ in this domain, not "invalid"
        ScenarioRequest request = request(BrokerType.RABBITMQ, null, 0, null);

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void should_throwBusinessException_when_rabbitmqQueueCapacityIsNegative() {
        ScenarioRequest request = request(BrokerType.RABBITMQ, null, -1, null);

        assertThatThrownBy(() -> validator.validate(request)).isInstanceOf(BusinessException.class);
    }

    @Test
    void should_throwBusinessException_when_sqsHasNoVisibilityTimeout() {
        ScenarioRequest request = request(BrokerType.SQS, null, null, null);

        assertThatThrownBy(() -> validator.validate(request)).isInstanceOf(BusinessException.class);
    }

    @Test
    void should_pass_when_sqsHasPositiveVisibilityTimeout() {
        ScenarioRequest request = request(BrokerType.SQS, null, null, 30);

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    private ScenarioRequest request(BrokerType broker, Integer partitions, Integer queueCapacity, Integer visTimeout) {
        return new ScenarioRequest(
                "cenário de teste",
                broker,
                100,
                2,
                10,
                BigDecimal.ONE,
                3,
                2,
                60,
                queueCapacity,
                partitions,
                visTimeout,
                true,
                false);
    }
}
