package com.catedra.turnos.service;

import com.catedra.turnos.client.CatedraClient;
import com.catedra.turnos.config.TurnosProperties;
import com.catedra.turnos.domain.AppUser;
import com.catedra.turnos.domain.ReservationProcess;
import com.catedra.turnos.domain.enums.ProcessStatus;
import com.catedra.turnos.dto.catedra.CatedraAppointmentDTO;
import com.catedra.turnos.repository.ReservationProcessRepository;
import com.catedra.turnos.web.ApiException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecoveryServiceTest {

    @Mock
    private ReservationProcessRepository processRepository;

    @Mock
    private CatedraClient catedraClient;

    private RecoveryService service;
    private ReservationProcess process;

    @BeforeEach
    void setUp() {
        service = new RecoveryService(
                processRepository,
                catedraClient,
                new TurnosProperties(),
                new SimpleMeterRegistry()
        );

        AppUser user = new AppUser();
        user.setId(1L);
        user.setLogin("juan.perez");

        process = new ReservationProcess();
        process.setProcessId("process-1");
        process.setUser(user);
        process.setExternalPatientId("juan.perez");
        process.setHoldId("hold-1");
        process.setStatus(ProcessStatus.WAITING_FOR_PHONE);
        process.setProfessionalId(15L);
        process.setAppointmentDate(LocalDate.now().plusDays(1));
        process.setStartTime(LocalTime.of(9, 0));
        process.setCreatedAt(Instant.now().minusSeconds(3600));
        process.setUpdatedAt(Instant.now().minusSeconds(3600));
    }

    @Test
    void expiresLocalProcessWithPastExpiration() {
        process.setExpiresAt(Instant.now().minusSeconds(60));
        when(processRepository.findAllByStatusInAndExpiresAtBefore(any(), any()))
                .thenReturn(List.of(process));

        int expired = service.expireLocalProcesses();

        assertThat(expired).isEqualTo(1);
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.EXPIRED);
        assertThat(process.getReason()).isEqualTo("HOLD_EXPIRED");
        verify(processRepository).save(process);
    }

    @Test
    void reconcilesStaleProcessWithRemoteConfirmation() {
        when(processRepository.findAllByStatusInAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of(process));
        when(catedraClient.findAppointment("process-1", "juan.perez"))
                .thenReturn(Optional.of(remote("CONFIRMED")));

        int reconciled = service.reconcileStaleProcesses();

        assertThat(reconciled).isEqualTo(1);
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.CONFIRMED);
        assertThat(process.getReservationId()).isEqualTo(8251L);
        assertThat(process.getReason()).isNull();
    }

    @Test
    void reconcilesConfirmedProcessCancelledRemotely() {
        process.setStatus(ProcessStatus.CONFIRMED);
        when(processRepository.findAllByStatusInAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of(process));
        when(catedraClient.findAppointment("process-1", "juan.perez"))
                .thenReturn(Optional.of(remote("CANCELLED")));

        int reconciled = service.reconcileStaleProcesses();

        assertThat(reconciled).isEqualTo(1);
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.CANCELLED);
        assertThat(process.getCancelledAt()).isNotNull();
        verify(processRepository).save(process);
    }

    @Test
    void doesNotExpireConfirmedProcess() {
        process.setStatus(ProcessStatus.CONFIRMED);
        process.setExpiresAt(Instant.now().minusSeconds(60));
        when(processRepository.findAllByStatusInAndExpiresAtBefore(any(), any()))
                .thenReturn(List.of(process));

        int expired = service.expireLocalProcesses();

        assertThat(expired).isZero();
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.CONFIRMED);
        verify(processRepository, never()).save(process);
    }

    @Test
    void keepsConfirmedProcessWhenRemoteReportsPending() {
        process.setStatus(ProcessStatus.CONFIRMED);
        when(processRepository.findAllByStatusInAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of(process));
        when(catedraClient.findAppointment("process-1", "juan.perez"))
                .thenReturn(Optional.of(remote("PHONE_PENDING")));

        int reconciled = service.reconcileStaleProcesses();

        assertThat(reconciled).isEqualTo(1);
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.CONFIRMED);
        verify(processRepository).save(process);
    }

    @Test
    void keepsConfirmedProcessWhenRemoteIsMissing() {
        process.setStatus(ProcessStatus.CONFIRMED);
        when(processRepository.findAllByStatusInAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of(process));
        when(catedraClient.findAppointment("process-1", "juan.perez"))
                .thenReturn(Optional.empty());

        int reconciled = service.reconcileStaleProcesses();

        assertThat(reconciled).isEqualTo(1);
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.CONFIRMED);
        assertThat(process.getReason()).isNull();
    }

    @Test
    void doesNotReconcileHeldProcess() {
        process.setStatus(ProcessStatus.HELD);
        when(processRepository.findAllByStatusInAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of(process));

        int reconciled = service.reconcileStaleProcesses();

        assertThat(reconciled).isZero();
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.HELD);
        verify(catedraClient, never()).findAppointment(any(), any());
        verify(processRepository, never()).save(process);
    }

    @Test
    void marksMissingRemoteAsExpiredOrInvalid() {
        when(processRepository.findAllByStatusInAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of(process));
        when(catedraClient.findAppointment("process-1", "juan.perez"))
                .thenReturn(Optional.empty());

        process.setExpiresAt(Instant.now().minusSeconds(60));
        service.reconcileStaleProcesses();
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.EXPIRED);

        process.setStatus(ProcessStatus.WAITING_FOR_PHONE);
        process.setExpiresAt(Instant.now().plusSeconds(600));
        service.reconcileStaleProcesses();
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.INVALID);
        assertThat(process.getReason()).isEqualTo("REMOTE_NOT_FOUND");
    }

    @Test
    void keepsProcessUnchangedWhenCatedraFails() {
        when(processRepository.findAllByStatusInAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of(process));
        when(catedraClient.findAppointment("process-1", "juan.perez"))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CATEDRA_UNAVAILABLE", "sin conexión"));

        int reconciled = service.reconcileStaleProcesses();

        assertThat(reconciled).isZero();
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.WAITING_FOR_PHONE);
        verify(processRepository, never()).save(process);
    }

    private CatedraAppointmentDTO remote(String status) {
        Instant cancelledAt = "CANCELLED".equals(status) ? Instant.now() : null;
        return new CatedraAppointmentDTO(
                8251L, "process-1", "juan.perez", LocalDate.now().plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 15L, "Ana", "Gomez", "Juan", "Perez",
                "+5492615551234", status, Instant.now(), null, cancelledAt, null
        );
    }
}
