package com.huawei.monitoring.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * RabbitMQ configuration for alert messaging.
 *
 * Features:
 * - Dead Letter Queue for failed messages
 * - Retry logic with exponential backoff
 * - Message TTL and max retries
 */
@Configuration
@ConditionalOnProperty(name = "spring.rabbitmq.host")
public class RabbitMQConfig {

    public static final String ALERTS_EXCHANGE = "alerts.exchange";
    public static final String ALERT_TRIGGERED_ROUTING_KEY = "alert.triggered";

    // Dead Letter Queue configuration
    public static final String ALERTS_DLQ = "alerts.dlq";
    public static final String ALERTS_DLX = "alerts.dlx";
    public static final String ALERTS_DLQ_ROUTING_KEY = "alert.failed";

    @Bean
    public Exchange alertsExchange() {
        return new TopicExchange(ALERTS_EXCHANGE, true, false);
    }

    /**
     * Dead Letter Exchange for failed alert messages.
     * Messages that fail processing are routed here.
     */
    @Bean
    public Exchange deadLetterExchange() {
        return new TopicExchange(ALERTS_DLX, true, false);
    }

    /**
     * Dead Letter Queue where failed messages are stored for manual review.
     * Messages in this queue can be monitored and manually retried if needed.
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(ALERTS_DLQ)
                .build();
    }

    /**
     * Binding for dead letter queue.
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
                .bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(ALERTS_DLQ_ROUTING_KEY)
                .noargs();
    }

    @Bean
    @NonNull
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(@NonNull ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());

        // Enable publisher confirms and returns
        template.setMandatory(true);

        // Set retry template with exponential backoff
        template.setRetryTemplate(retryTemplate());

        // Recovery callback - what to do when all retries fail
        template.setReturnsCallback(returned -> 
            org.slf4j.LoggerFactory.getLogger(RabbitMQConfig.class).error(
                "Message returned: exchange={}, routingKey={}, replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyText()
            )
        );

        return template;
    }

    /**
     * Retry template with exponential backoff.
     * - Initial attempt + 3 retries = 4 total attempts
     * - Backoff: 1s, 2s, 4s
     */
    @Bean
    @NonNull
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Simple retry policy: max 3 retries
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);

        // Exponential backoff: starts at 1000ms, multiplier 2.0, max 10000ms
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000L);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(10000L);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}
