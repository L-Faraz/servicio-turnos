package com.catedra.turnos.service;

import com.catedra.turnos.config.TurnosProperties;
import com.catedra.turnos.domain.OutboxEvent;
import com.catedra.turnos.domain.enums.OutboxStatus;
import com.catedra.turnos.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxService {

    private static final int MAX_ERROR_LENGTH = 500;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TurnosProperties properties;
    private final MeterRegistry meterRegistry;

    public OutboxService(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            TurnosProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void enqueue(String eventId, String topic, String messageKey, Object payload) {
        if (!StringUtils.hasText(eventId) || repository.existsByEventId(eventId)) {
            return;
        }

        Instant now = Instant.now();
        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setTopic(topic);
        event.setMessageKey(messageKey);
        event.setPayload(serialize(payload));
        event.setStatus(OutboxStatus.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(now);
        event.setCreatedAt(now);
        repository.saveAndFlush(event);
    }

    @Transactional
    public int publishPending() {
        Instant now = Instant.now();
        List<OutboxEvent> events = repository.findAllByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
                List.of(OutboxStatus.PENDING, OutboxStatus.FAILED),
                now
        );

        for (OutboxEvent event : events) {
            int attempt = event.getAttempts() + 1;
            event.setAttempts(attempt);
            try {
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                event.setNextAttemptAt(now);
                event.setLastError(null);
                meterRegistry.counter("turnos.outbox.events", "outcome", "published").increment();
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                event.setStatus(OutboxStatus.FAILED);
                event.setLastError(trim(exception.getMessage()));
                if (attempt >= properties.getOutbox().getMaxAttempts()) {
                    event.setNextAttemptAt(now.plusSeconds(31536000));
                    meterRegistry.counter("turnos.outbox.events", "outcome", "exhausted").increment();
                } else {
                    long delay = properties.getOutbox().getRetryInitialMs()
                            * (long) Math.pow(properties.getOutbox().getRetryMultiplier(), attempt - 1);
                    event.setNextAttemptAt(now.plusMillis(delay));
                    meterRegistry.counter("turnos.outbox.events", "outcome", "failed").increment();
                }
            }
        }

        if (!events.isEmpty()) {
            repository.saveAll(events);
        }
        return events.size();
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar el evento del outbox", exception);
        }
    }

    private String trim(String message) {
        if (!StringUtils.hasText(message)) {
            return "ERROR";
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
