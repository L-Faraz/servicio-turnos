package com.catedra.turnos.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("${turnos.outbox.enabled:true} and ${catedra.kafka-enabled:true}")
public class OutboxPublisher {

    private final OutboxService outboxService;

    public OutboxPublisher(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Scheduled(fixedDelayString = "${turnos.outbox.publish-interval-ms:2000}")
    public void publish() {
        outboxService.publishPending();
    }
}
