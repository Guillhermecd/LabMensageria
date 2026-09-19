package com.bimd.msgsim.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** spring-boot-starter-amqp autowires ConnectionFactory/RabbitTemplate but not RabbitAdmin —
 * {@code RabbitMqBrokerAdapter} needs it to declare/purge/delete per-run queues. */
@Configuration
public class RabbitConfig {

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
}
