package com.catedra.turnos.service;

import com.catedra.turnos.domain.ReservationProcess;
import com.catedra.turnos.domain.enums.ProcessStatus;
import com.catedra.turnos.dto.TurnosEventDTO;
import com.catedra.turnos.repository.ReservationProcessRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationEventServiceTest {

    @Mock
    private ReservationProcessRepository processRepository;

    private ReservationEventService eventService;
    private ReservationProcess process;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        eventService = new ReservationEventService(processRepository, meterRegistry);
        process = new ReservationProcess();
        process.setProcessId("process-1");
        process.setStatus(ProcessStatus.WAITING_FOR_PHONE);
        process.setRequestEventId("request-1");
        process.setPatientPhone("+5492615551234");
        process.setPhoneAttempt(0);
        when(processRepository.findById("process-1")).thenReturn(Optional.of(process));
    }

    @Test
    void requestedStoresRequestEventAndExpiration() {
        eventService.handle(event(
                "request-2",
                "AdditionalInformationRequested",
                Instant.now().plusSeconds(600)
        ));

        assertThat(process.getStatus()).isEqualTo(ProcessStatus.WAITING_FOR_PHONE);
        assertThat(process.getRequestEventId()).isEqualTo("request-2");
        assertThat(process.getPatientPhone()).isNull();
        assertThat(process.getExpiresAt()).isNotNull();
    }

    @Test
    void rejectedMovesBackToWaitingAndIncrementsPhoneAttempt() {
        eventService.handle(event(
                "rejection-1",
                "AdditionalInformationRejected",
                Instant.now().plusSeconds(600)
        ));

        assertThat(process.getStatus()).isEqualTo(ProcessStatus.WAITING_FOR_PHONE);
        assertThat(process.getPhoneAttempt()).isEqualTo(1);
        assertThat(process.getPatientPhone()).isNull();
        assertThat(process.getReason()).isEqualTo("INVALID_PHONE_NUMBER");
    }

    @Test
    void confirmedUpdatesStatusAndReservationId() {
        eventService.handle(new TurnosEventDTO(
                "confirm-1", "AppointmentConfirmed", "proyecto-prueba", "process-1", "hold-1",
                "request-1", null, null, null, "CONFIRMED", 8251L, 15L, null, null, null,
                Instant.now(), Map.of(), 1
        ));

        assertThat(process.getStatus()).isEqualTo(ProcessStatus.CONFIRMED);
        assertThat(process.getReservationId()).isEqualTo(8251L);
    }

    @Test
    void expiredInvalidAndCancelledArePersisted() {
        eventService.handle(event("expired-1", "AppointmentProcessExpired", null));
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.EXPIRED);

        process.setStatus(ProcessStatus.WAITING_FOR_PHONE);
        eventService.handle(event("invalid-1", "AppointmentProcessInvalid", null));
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.INVALID);

        process.setStatus(ProcessStatus.CONFIRMED);
        eventService.handle(event("cancel-1", "AppointmentCancelled", null));
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.CANCELLED);
        assertThat(meterRegistry.find("turnos.kafka.events")
                .tags("outcome", "late_final")
                .counter()).isNull();
    }

    @Test
    void lateConfirmationDoesNotReopenFinalState() {
        process.setStatus(ProcessStatus.EXPIRED);

        eventService.handle(new TurnosEventDTO(
                "confirm-late", "AppointmentConfirmed", "proyecto-prueba", "process-1", "hold-1",
                "request-1", null, null, null, "CONFIRMED", 8251L, 15L, null, null, null,
                Instant.now(), java.util.Map.of(), 1
        ));

        assertThat(process.getStatus()).isEqualTo(ProcessStatus.EXPIRED);
    }

    private TurnosEventDTO event(String eventId, String eventType, Instant expiresAt) {
        return new TurnosEventDTO(
                eventId, eventType, "proyecto-prueba", "process-1", "hold-1", "request-1",
                "PHONE_NUMBER", "mensaje", "INVALID_PHONE_NUMBER", null, null, null, null, null,
                expiresAt, Instant.now(), Map.of(), 1
        );
    }
}
