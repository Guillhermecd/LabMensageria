package com.bimd.msgsim.service.real;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.domain.model.Scenario;
import com.rabbitmq.client.Channel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Talks to a real RabbitMQ over spring-amqp. Retry/DLQ decisions come from the injected
 * {@link MessageProcessor} — this class only does the mechanical ack/republish/dead-letter,
 * so the failure-simulation logic in {@code RealSimulationService} stays broker-agnostic.
 */
@Component
@RequiredArgsConstructor
public class RabbitMqBrokerAdapter implements MessageBrokerPort {

    private static final String RETRY_HEADER = "x-msgsim-retry-count";

    private final ConnectionFactory connectionFactory;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;
    private final Map<String, SimpleMessageListenerContainer> containers = new ConcurrentHashMap<>();

    @Override
    public BrokerType type() {
        return BrokerType.RABBITMQ;
    }

    @Override
    public void setUp(String queueName, Scenario scenario) {
        Map<String, Object> args = new HashMap<>();
        if (scenario.getQueueCapacity() != null && scenario.getQueueCapacity() > 0) {
            args.put("x-max-length", scenario.getQueueCapacity());
            args.put("x-overflow", "drop-head");
        }
        rabbitAdmin.declareQueue(new Queue(queueName, false, false, false, args));
        if (scenario.isDlqEnabled()) {
            rabbitAdmin.declareQueue(new Queue(dlqName(queueName), false, false, false));
        }
    }

    @Override
    public void publish(String queueName, byte[] payload, int retryCount) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader(RETRY_HEADER, retryCount);
        rabbitTemplate.send(queueName, new Message(payload, properties));
    }

    @Override
    public void registerConsumer(String queueName, int concurrency, MessageProcessor processor) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(queueName);
        container.setConcurrentConsumers(Math.max(1, concurrency));
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        container.setMessageListener((org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener)
                (message, channel) -> handleMessage(message, channel, queueName, processor));
        container.start();
        containers.put(queueName, container);
    }

    private void handleMessage(Message message, Channel channel, String queueName, MessageProcessor processor)
            throws java.io.IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        int retryCount = (int) message.getMessageProperties().getHeaders().getOrDefault(RETRY_HEADER, 0);
        ProcessResult result = processor.process(message.getBody(), retryCount);
        switch (result) {
            case OK, DROP -> channel.basicAck(deliveryTag, false);
            case RETRY -> {
                channel.basicAck(deliveryTag, false);
                publish(queueName, message.getBody(), retryCount + 1);
            }
            case DLQ -> {
                channel.basicAck(deliveryTag, false);
                rabbitTemplate.send(dlqName(queueName), message);
            }
        }
    }

    @Override
    public void stopConsumer(String queueName) {
        SimpleMessageListenerContainer container = containers.remove(queueName);
        if (container != null) {
            container.stop();
        }
    }

    @Override
    public long queueDepth(String queueName) {
        try {
            return rabbitTemplate.execute(channel -> channel.queueDeclarePassive(queueName).getMessageCount());
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void tearDown(String queueName) {
        try {
            rabbitAdmin.purgeQueue(queueName, false);
            rabbitAdmin.deleteQueue(queueName);
            rabbitAdmin.deleteQueue(dlqName(queueName));
        } catch (Exception e) {
            // best-effort cleanup — a queue left behind is a smaller problem than a run that never finishes
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            return rabbitTemplate.execute(Channel::isOpen);
        } catch (Exception e) {
            return false;
        }
    }

    private String dlqName(String queueName) {
        return queueName + ".dlq";
    }
}
