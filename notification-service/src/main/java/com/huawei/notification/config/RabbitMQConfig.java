package com.huawei.notification.config;

import java.util.Objects;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.huawei.common.constants.MessagingConstants;

@Configuration
public class RabbitMQConfig {

    // Re-export constants for backward compatibility with existing code
    public static final String NOTIFICATION_QUEUE = MessagingConstants.NOTIFICATION_QUEUE;
    public static final String ALERTS_EXCHANGE = MessagingConstants.ALERTS_EXCHANGE;
    public static final String ALERT_TRIGGERED_ROUTING_KEY = MessagingConstants.ALERT_TRIGGERED_ROUTING_KEY;

    @Bean
    public Exchange alertsExchange() {
        return new TopicExchange(ALERTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return new Queue(NOTIFICATION_QUEUE, true);
    }

    @Bean
    public Binding alertBinding(Queue notificationQueue, Exchange alertsExchange) {
        return BindingBuilder
                .bind(notificationQueue)
                .to(alertsExchange)
                .with(ALERT_TRIGGERED_ROUTING_KEY)
                .noargs();
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(
                Objects.requireNonNull(connectionFactory, "ConnectionFactory cannot be null"));
        template.setMessageConverter(
                Objects.requireNonNull(messageConverter(), "Message converter cannot be null"));
        return template;
    }
}
