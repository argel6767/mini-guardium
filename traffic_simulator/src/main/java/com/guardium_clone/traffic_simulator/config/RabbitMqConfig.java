package com.guardium_clone.traffic_simulator.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({RawEventMessagingProperties.class, TrafficSimulatorProperties.class})
public class RabbitMqConfig {

    @Bean
    DirectExchange rawIngestionEventsExchange(RawEventMessagingProperties properties) {
        return ExchangeBuilder.directExchange(properties.exchange())
                .durable(true)
                .build();
    }

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}