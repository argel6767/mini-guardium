package com.guardium_clone.traffic_simulator.messaging;

import com.guardium_clone.messaging.RawIngestionEventMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.guardium_clone.traffic_simulator.api.IngestEventRequest;
import com.guardium_clone.traffic_simulator.config.RawEventMessagingProperties;
import com.guardium_clone.traffic_simulator.model.QueryType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RawIngestionEventPublisherTests {

    @Test
    void publishSendsRawIngestionEventMessageToConfiguredExchangeAndRoutingKey() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RawIngestionEventPublisher publisher = new RawIngestionEventPublisher(
                rabbitTemplate,
                new RawEventMessagingProperties("ingestion.exchange", "ingestion.created")
        );
        IngestEventRequest request = new IngestEventRequest(
                "alice",
                "customer_accounts",
                QueryType.SELECT,
                Instant.parse("2026-07-02T22:30:00Z"),
                42,
                "10.0.0.12",
                "select * from customer_accounts"
        );

        RawIngestionEventMessage message = publisher.publish(request);

        ArgumentCaptor<RawIngestionEventMessage> messageCaptor = ArgumentCaptor.forClass(RawIngestionEventMessage.class);
        verify(rabbitTemplate).convertAndSend(eq("ingestion.exchange"), eq("ingestion.created"), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).isEqualTo(message);
        assertThat(message.simulatedEventId()).isNotNull();
        assertThat(message.username()).isEqualTo("alice");
        assertThat(message.tableName()).isEqualTo("customer_accounts");
        assertThat(message.queryType()).isEqualTo("SELECT");
        assertThat(message.occurredAt()).isEqualTo(Instant.parse("2026-07-02T22:30:00Z"));
        assertThat(message.rowCount()).isEqualTo(42);
        assertThat(message.sourceIp()).isEqualTo("10.0.0.12");
        assertThat(message.queryText()).isEqualTo("select * from customer_accounts");
    }
}