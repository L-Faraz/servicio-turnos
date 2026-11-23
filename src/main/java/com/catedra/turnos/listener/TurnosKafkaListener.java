package com.catedra.turnos.listener;

import com.catedra.turnos.config.CatedraProperties;
import com.catedra.turnos.dto.TurnosEventDTO;
import com.catedra.turnos.service.IdempotencyService;
import com.catedra.turnos.service.ReservationEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(name = "catedra.kafka-enabled", havingValue = "true", matchIfMissing = true)
public class TurnosKafkaListener {

    private final ObjectMapper objectMapper;
    private final ReservationEventService eventService;
    private final IdempotencyService idempotencyService;
    private final CatedraProperties properties;
    private final MeterRegistry meterRegistry;

    public TurnosKafkaListener(
            ObjectMapper objectMapper,
            ReservationEventService eventService,
            IdempotencyService idempotencyService,
            CatedraProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.eventService = eventService;
        this.idempotencyService = idempotencyService;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = "#{@catedraProperties.effectiveActionsTopic}",
            groupId = "#{@catedraProperties.effectiveConsumerGroupId}",
            autoStartup = "${catedra.kafka-enabled:true}"
    )
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        TurnosEventDTO event;
        try {
            event = objectMapper.readValue(record.value(), TurnosEventDTO.class);
        } catch (Exception exception) {
            meterRegistry.counter("turnos.kafka.events", "outcome", "invalid_json").increment();
            throw new IllegalArgumentException("No se pudo deserializar el evento de turnos", exception);
        }

        validate(record, event);

        if (idempotencyService.isProcessed(event.eventId())
                || !idempotencyService.tryMarkProcessed(event.eventId(), event.eventType())) {
            meterRegistry.counter("turnos.kafka.events", "outcome", "duplicate").increment();
            acknowledgment.acknowledge();
            return;
        }

        eventService.handle(event);
        meterRegistry.counter("turnos.kafka.events", "outcome", "processed", "type", event.eventType()).increment();
        acknowledgment.acknowledge();
    }

    private void validate(ConsumerRecord<String, String> record, TurnosEventDTO event) {
        if (event == null
                || !StringUtils.hasText(event.eventId())
                || !StringUtils.hasText(event.eventType())
                || !StringUtils.hasText(event.reservationProcessId())
                || event.schemaVersion() == null
                || event.schemaVersion() != 1) {
            throw new IllegalArgumentException("El evento de turnos no cumple el contrato");
        }
        if (!event.reservationProcessId().equals(record.key())) {
            throw new IllegalArgumentException("La message key no coincide con reservationProcessId");
        }
        if (StringUtils.hasText(event.groupId())
                && !event.groupId().equals(properties.getEffectiveGroupId())) {
            throw new IllegalArgumentException("El groupId del evento no corresponde a esta instalación");
        }
        require(event.occurredAt() != null, "occurredAt");

        switch (event.eventType()) {
            case "AdditionalInformationRequested" -> {
                require(StringUtils.hasText(event.holdId()), "holdId");
                require(StringUtils.hasText(event.requiredField()), "requiredField");
                require(event.expiresAt() != null, "expiresAt");
            }
            case "AppointmentConfirmed" -> {
                require(StringUtils.hasText(event.holdId()), "holdId");
                require(event.reservationId() != null, "reservationId");
                require(event.professionalId() != null, "professionalId");
                require(event.date() != null, "date");
                require(event.startTime() != null, "startTime");
            }
            case "AdditionalInformationRejected" -> {
                require(StringUtils.hasText(event.holdId()), "holdId");
                require(StringUtils.hasText(event.requestEventId()), "requestEventId");
                require(StringUtils.hasText(event.reason()), "reason");
                require(event.expiresAt() != null, "expiresAt");
            }
            case "AppointmentProcessExpired", "AppointmentProcessInvalid" -> {
                require(StringUtils.hasText(event.holdId()), "holdId");
                require(StringUtils.hasText(event.reason()), "reason");
            }
            case "AppointmentCancelled" -> {
                require(StringUtils.hasText(event.holdId()), "holdId");
                require(event.reservationId() != null, "reservationId");
                require(event.date() != null, "date");
                require(event.startTime() != null, "startTime");
            }
            default -> {
            }
        }
    }

    private void require(boolean condition, String field) {
        if (!condition) {
            throw new IllegalArgumentException("El evento de turnos no cumple el contrato: falta " + field);
        }
    }
}
