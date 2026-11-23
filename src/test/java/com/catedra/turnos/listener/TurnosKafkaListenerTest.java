package com.catedra.turnos.listener;

import com.catedra.turnos.config.CatedraProperties;
import com.catedra.turnos.dto.TurnosEventDTO;
import com.catedra.turnos.service.IdempotencyService;
import com.catedra.turnos.service.ReservationEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TurnosKafkaListenerTest {

    @Mock
    private ReservationEventService eventService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private Acknowledgment acknowledgment;

    private TurnosKafkaListener listener;

    @BeforeEach
    void setUp() {
        listener = new TurnosKafkaListener(
                new ObjectMapper(),
                eventService,
                idempotencyService,
                new CatedraProperties(),
                new SimpleMeterRegistry()
        );
    }

    @Test
    void processesValidEventAndAcknowledges() {
        listener.onMessage(record(validPayload()), acknowledgment);

        verify(eventService).handle(any(TurnosEventDTO.class));
        verify(idempotencyService).markProcessed("event-1", "AdditionalInformationRejected");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void skipsDuplicatedEvent() {
        when(idempotencyService.isProcessed("event-1")).thenReturn(true);

        listener.onMessage(record(validPayload()), acknowledgment);

        verify(eventService, never()).handle(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void rejectsWrongMessageKey() {
        String payload = validPayload().replace("process-1", "process-2");

        assertThatThrownBy(() -> listener.onMessage(record(payload), acknowledgment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message key");

        verify(eventService, never()).handle(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void rejectsWrongSchemaVersion() {
        String payload = validPayload().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2");

        assertThatThrownBy(() -> listener.onMessage(record(payload), acknowledgment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contrato");

        verify(eventService, never()).handle(any(TurnosEventDTO.class));
    }

    private ConsumerRecord<String, String> record(String payload) {
        return new ConsumerRecord<>("alumnos.turnos.acciones.proyecto-l-faraz", 0, 0L, "process-1", payload);
    }

    private String validPayload() {
        return """
                {
                  "eventId": "event-1",
                  "eventType": "AdditionalInformationRejected",
                  "groupId": "proyecto-l-faraz",
                  "reservationProcessId": "process-1",
                  "holdId": "hold-1",
                  "requestEventId": "request-1",
                  "reason": "INVALID_PHONE_NUMBER",
                  "message": "Telefono invalido",
                  "schemaVersion": 1
                }
                """;
    }
}
