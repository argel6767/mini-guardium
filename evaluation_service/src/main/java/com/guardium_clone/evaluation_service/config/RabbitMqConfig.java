package com.guardium_clone.evaluation_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AccessEventMessagingProperties.class)
public class RabbitMqConfig {

    @Bean
    DirectExchange accessEventsExchange(AccessEventMessagingProperties properties) {
        return ExchangeBuilder.directExchange(properties.exchange())
                .durable(true)
                .build();
    }

    @Bean
    Queue accessEventsQueue(AccessEventMessagingProperties properties) {
        return QueueBuilder.durable(properties.queue()).build();
    }

    @Bean
    Binding accessEventsBinding(
            Queue accessEventsQueue,
            DirectExchange accessEventsExchange,
            AccessEventMessagingProperties properties
    ) {
        return BindingBuilder.bind(accessEventsQueue)
                .to(accessEventsExchange)
                .with(properties.routingKey());
    }

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
