package com.guardium_clone.evaluation_service.messaging;

import com.guardium_clone.evaluation_service.service.AccessEventEvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AccessEventCreatedListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessEventCreatedListener.class);

    private final AccessEventEvaluationService evaluationService;

    public AccessEventCreatedListener(AccessEventEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @RabbitListener(queues = "${evaluation.access-events.queue:guardium.access-events.evaluation-service}")
    public void receive(AccessEventCreatedMessage message) {
        LOGGER.debug(
                "Received access event created message accessEventId={}, username={}, tableName={}, queryType={}",
                message.accessEventId(),
                message.username(),
                message.tableName(),
                message.queryType()
        );
        evaluationService.evaluate(message);
    }
}
