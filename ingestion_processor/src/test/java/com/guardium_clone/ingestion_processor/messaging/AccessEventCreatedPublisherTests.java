package com.guardium_clone.ingestion_processor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.guardium_clone.ingestion_processor.config.AccessEventMessagingProperties;
import com.guardium_clone.ingestion_processor.model.AccessEvent;
import com.guardium_clone.ingestion_processor.model.DatabaseTable;
import com.guardium_clone.ingestion_processor.model.DatabaseUser;
import com.guardium_clone.ingestion_processor.model.QueryType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class AccessEventCreatedPublisherTests {

    @Test
    void publishesAccessEventCreatedMessageToConfiguredExchangeAndRoutingKey() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AccessEventCreatedPublisher publisher = new AccessEventCreatedPublisher(
                rabbitTemplate,
                new AccessEventMessagingProperties("events.exchange", "access.created")
        );
        AccessEvent accessEvent = new AccessEvent(
                new DatabaseUser("alice"),
                new DatabaseTable("customer_accounts", false),
                QueryType.SELECT,
                Instant.parse("2026-07-02T22:30:00Z"),
                42,
                "10.0.0.12",
                "select * from customer_accounts"
        );
        ReflectionTestUtils.setField(accessEvent, "id", 99L);

        publisher.publish(accessEvent);

        ArgumentCaptor<AccessEventCreatedMessage> messageCaptor = ArgumentCaptor.forClass(AccessEventCreatedMessage.class);
        verify(rabbitTemplate).convertAndSend(eq("events.exchange"), eq("access.created"), messageCaptor.capture());
        assertThat(messageCaptor.getValue())
                .isEqualTo(new AccessEventCreatedMessage(
                        99L,
                        "alice",
                        "customer_accounts",
                        "SELECT",
                        Instant.parse("2026-07-02T22:30:00Z"),
                        42,
                        "10.0.0.12",
                        "select * from customer_accounts"
                ));
    }
}