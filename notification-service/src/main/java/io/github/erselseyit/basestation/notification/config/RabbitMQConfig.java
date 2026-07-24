package io.github.erselseyit.basestation.notification.config;

import java.util.Objects;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.erselseyit.basestation.common.constants.MessagingConstants;

@Configuration
public class RabbitMQConfig {

    // Re-export constants for backward compatibility with existing code
    public static final String NOTIFICATION_QUEUE = MessagingConstants.NOTIFICATION_QUEUE;
    public static final String ALERTS_EXCHANGE = MessagingConstants.ALERTS_EXCHANGE;
    public static final String ALERT_TRIGGERED_ROUTING_KEY = MessagingConstants.ALERT_TRIGGERED_ROUTING_KEY;
    public static final String DIAGNOSTIC_RESOLUTION_QUEUE = MessagingConstants.DIAGNOSTIC_RESOLUTION_QUEUE;
    public static final String DIAGNOSTIC_RESOLVED_ROUTING_KEY = MessagingConstants.DIAGNOSTIC_RESOLVED_ROUTING_KEY;

    /** Dead-letter exchange and queue: messages that exhaust retries land here
     *  instead of being redelivered forever or dropped (Nygard: fail fast, keep
     *  a record). */
    public static final String DEAD_LETTER_EXCHANGE = "alerts.dlx";
    public static final String DEAD_LETTER_QUEUE = "notifications.dlq";

    @Bean
    public Exchange alertsExchange() {
        return new TopicExchange(ALERTS_EXCHANGE, true, false);
    }

    @Bean
    public org.springframework.amqp.core.FanoutExchange deadLetterExchange() {
        return new org.springframework.amqp.core.FanoutExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return new Queue(DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue,
            org.springframework.amqp.core.FanoutExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange);
    }

    @Bean
    public Queue notificationQueue() {
        return org.springframework.amqp.core.QueueBuilder.durable(NOTIFICATION_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding alertBinding(Queue notificationQueue, Exchange alertsExchange) {
        return BindingBuilder
                .bind(notificationQueue)
                .to(alertsExchange)
                .with(ALERT_TRIGGERED_ROUTING_KEY)
                .noargs();
    }

    @Bean("diagnosticResolutionQueue")
    public Queue diagnosticResolutionQueue() {
        return org.springframework.amqp.core.QueueBuilder.durable(DIAGNOSTIC_RESOLUTION_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding diagnosticResolutionBinding(Queue diagnosticResolutionQueue, Exchange alertsExchange) {
        return BindingBuilder
                .bind(diagnosticResolutionQueue)
                .to(alertsExchange)
                .with(DIAGNOSTIC_RESOLVED_ROUTING_KEY)
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

    /**
     * Overrides Spring Boot's default listener container factory to keep all of
     * its defaults (via the configurer) and add the correlation-id advice, so
     * every {@code @RabbitListener} restores the inbound correlation id into the
     * MDC for the duration of processing.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAdviceChain(new CorrelationIdInboundAdvice());
        // A message that keeps failing must not be requeued forever; reject it
        // so it is routed to the dead-letter exchange for inspection/replay.
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
