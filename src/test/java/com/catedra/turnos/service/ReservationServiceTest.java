package com.catedra.turnos.service;

import com.catedra.turnos.client.CatalogoClient;
import com.catedra.turnos.client.CatedraClient;
import com.catedra.turnos.config.CatedraProperties;
import com.catedra.turnos.domain.AppointmentHold;
import com.catedra.turnos.domain.AppUser;
import com.catedra.turnos.domain.ReservationProcess;
import com.catedra.turnos.domain.enums.ProcessStatus;
import com.catedra.turnos.dto.AdditionalInformationSubmittedEvent;
import com.catedra.turnos.dto.AppointmentPageDTO;
import com.catedra.turnos.dto.AvailableSlotDTO;
import com.catedra.turnos.dto.ConfirmedHoldResponse;
import com.catedra.turnos.dto.CreateHoldRequest;
import com.catedra.turnos.dto.CreatedHoldResponse;
import com.catedra.turnos.dto.catalogo.CatalogProfessionalDTO;
import com.catedra.turnos.dto.catalogo.WeeklyScheduleDTO;
import com.catedra.turnos.dto.catedra.AppointmentPage;
import com.catedra.turnos.dto.catedra.CancelResponseDTO;
import com.catedra.turnos.dto.catedra.CatedraAppointmentDTO;
import com.catedra.turnos.dto.catedra.ConfirmHoldResponseDTO;
import com.catedra.turnos.dto.catedra.HoldResponseDTO;
import com.catedra.turnos.repository.AppointmentHoldRepository;
import com.catedra.turnos.repository.ReservationProcessRepository;
import com.catedra.turnos.web.ApiException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private CatalogoClient catalogoClient;

    @Mock
    private CatedraClient catedraClient;

    @Mock
    private AvailabilityService availabilityService;

    @Mock
    private ReservationProcessRepository processRepository;

    @Mock
    private AppointmentHoldRepository holdRepository;

    @Mock
    private OutboxService outboxService;

    private ReservationService reservationService;
    private AppUser user;
    private LocalDate date;
    private Instant expiresAt;

    @BeforeEach
    void setUp() {
        CatedraProperties properties = new CatedraProperties();
        properties.setGroupId("proyecto-prueba");
        reservationService = new ReservationService(
                catalogoClient,
                catedraClient,
                availabilityService,
                processRepository,
                holdRepository,
                outboxService,
                properties,
                new SimpleMeterRegistry()
        );

        user = new AppUser();
        user.setId(1L);
        user.setLogin("juan.perez");
        user.setFirstName("Juan");
        user.setLastName("Perez");

        date = LocalDate.now().plusDays(2);
        expiresAt = Instant.now().plusSeconds(900);
    }

    @Test
    void createHoldPersistsLocalProcessAndHold() {
        CreateHoldRequest request = new CreateHoldRequest(15L, date, LocalTime.of(9, 0));
        WeeklyScheduleDTO schedule = new WeeklyScheduleDTO(
                1L, 15L, DayOfWeek.of(date.getDayOfWeek().getValue()),
                LocalTime.of(9, 0), LocalTime.of(10, 0), 30, true
        );
        CatalogProfessionalDTO professional = new CatalogProfessionalDTO(
                15L, "Ana", "Gomez", 3L, "Cardiologia", true, List.of(schedule)
        );

        when(catalogoClient.getProfessional(15L)).thenReturn(professional);
        when(availabilityService.getAvailability(user, 15L, date, date)).thenReturn(List.of(
                new AvailableSlotDTO(15L, date, LocalTime.of(9, 0), LocalTime.of(9, 30), true, null)
        ));
        when(catedraClient.createHold(any())).thenReturn(
                new HoldResponseDTO("hold-1", "process-1", "HELD", expiresAt)
        );
        when(processRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(holdRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreatedHoldResponse response = reservationService.createHold(user, request);

        ArgumentCaptor<ReservationProcess> processCaptor = ArgumentCaptor.forClass(ReservationProcess.class);
        ArgumentCaptor<AppointmentHold> holdCaptor = ArgumentCaptor.forClass(AppointmentHold.class);
        verify(processRepository).save(processCaptor.capture());
        verify(holdRepository).save(holdCaptor.capture());

        assertThat(response.holdId()).isEqualTo("hold-1");
        assertThat(response.processId()).isEqualTo("process-1");
        assertThat(processCaptor.getValue().getExternalPatientId()).isEqualTo("juan.perez");
        assertThat(processCaptor.getValue().getStatus()).isEqualTo(ProcessStatus.HELD);
        assertThat(holdCaptor.getValue().getHoldId()).isEqualTo("hold-1");
        assertThat(holdCaptor.getValue().getEndTime()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    void createHoldRejectsSlotWithActiveHold() {
        CreateHoldRequest request = new CreateHoldRequest(15L, date, LocalTime.of(9, 0));
        when(catalogoClient.getProfessional(15L)).thenReturn(enabledProfessional());
        when(availabilityService.getAvailability(user, 15L, date, date)).thenReturn(List.of(
                new AvailableSlotDTO(15L, date, LocalTime.of(9, 0), LocalTime.of(9, 30), false, "HELD")
        ));

        assertThatThrownBy(() -> reservationService.createHold(user, request))
                .isInstanceOfSatisfying(com.catedra.turnos.web.ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("SLOT_ALREADY_HELD");
                });

        verify(catedraClient, never()).createHold(any());
    }

    @Test
    void confirmHoldMovesProcessToWaitingForPhone() {
        AppointmentHold hold = heldHold(expiresAt);
        ReservationProcess process = heldProcess();
        when(holdRepository.findByHoldIdAndUserId("hold-1", 1L)).thenReturn(Optional.of(hold));
        when(processRepository.findByHoldIdAndUserId("hold-1", 1L)).thenReturn(Optional.of(process));
        when(catalogoClient.getProfessional(15L)).thenReturn(enabledProfessional());
        when(catedraClient.confirmHold(eq("hold-1"), any())).thenReturn(
                new ConfirmHoldResponseDTO("process-1", "WAITING_FOR_PHONE", expiresAt)
        );

        ConfirmedHoldResponse response = reservationService.confirmHold(user, "hold-1");

        assertThat(response.status()).isEqualTo("WAITING_FOR_PHONE");
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.WAITING_FOR_PHONE);
        assertThat(hold.getStatus()).isEqualTo("CONFIRMED");
        verify(catalogoClient).getProfessional(15L);
        verify(catedraClient).confirmHold(eq("hold-1"), org.mockito.ArgumentMatchers.argThat(request ->
                "juan.perez".equals(request.externalPatientId())
                        && "Juan".equals(request.patientFirstName())
                        && "Perez".equals(request.patientLastName())
        ));
    }

    @Test
    void rejectsHoldBelongingToAnotherUser() {
        when(holdRepository.findByHoldIdAndUserId("hold-other", 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.confirmHold(user, "hold-other"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("HOLD_NOT_FOUND");
                });
    }

    @Test
    void rejectsExpiredHold() {
        AppointmentHold hold = heldHold(Instant.now().minusSeconds(60));
        when(holdRepository.findByHoldIdAndUserId("hold-1", 1L)).thenReturn(Optional.of(hold));
        when(processRepository.findByHoldIdAndUserId("hold-1", 1L)).thenReturn(Optional.of(heldProcess()));
        when(catalogoClient.getProfessional(15L)).thenReturn(enabledProfessional());

        assertThatThrownBy(() -> reservationService.confirmHold(user, "hold-1"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("HOLD_EXPIRED")
                );
    }

    @Test
    void confirmHoldRevalidatesProfessionalBeforeCallingCatedra() {
        AppointmentHold hold = heldHold(expiresAt);
        when(holdRepository.findByHoldIdAndUserId("hold-1", 1L)).thenReturn(Optional.of(hold));
        when(processRepository.findByHoldIdAndUserId("hold-1", 1L)).thenReturn(Optional.of(heldProcess()));
        when(catalogoClient.getProfessional(15L)).thenReturn(new CatalogProfessionalDTO(
                15L, "Ana", "Gomez", 3L, "Cardiologia", false, List.of()
        ));

        assertThatThrownBy(() -> reservationService.confirmHold(user, "hold-1"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("PROFESSIONAL_DISABLED")
                );
        verify(catedraClient, never()).confirmHold(any(), any());
    }

    @Test
    void submitPhoneEnqueuesAdditionalInformationSubmitted() {
        ReservationProcess process = waitingProcess();
        when(processRepository.findByProcessIdAndUserId("process-1", 1L)).thenReturn(Optional.of(process));

        reservationService.submitPhone(user, "process-1", "+549 261 555-1234");

        ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).enqueue(
                eventIdCaptor.capture(),
                topicCaptor.capture(),
                keyCaptor.capture(),
                payloadCaptor.capture()
        );

        assertThat(eventIdCaptor.getValue()).startsWith("phone-");
        assertThat(topicCaptor.getValue()).isEqualTo("catedra.turnos.telefono.proyecto-prueba");
        assertThat(keyCaptor.getValue()).isEqualTo("process-1");
        assertThat(payloadCaptor.getValue()).isInstanceOf(AdditionalInformationSubmittedEvent.class);
        AdditionalInformationSubmittedEvent event = (AdditionalInformationSubmittedEvent) payloadCaptor.getValue();
        assertThat(event.eventType()).isEqualTo("AdditionalInformationSubmitted");
        assertThat(event.requestEventId()).isEqualTo("request-1");
        assertThat(event.information()).containsEntry("phoneNumber", "+5492615551234");
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.PHONE_SUBMITTED);
        assertThat(process.getPatientPhone()).isEqualTo("+5492615551234");
    }

    @Test
    void submitPhoneIsIdempotentForSamePhone() {
        ReservationProcess process = waitingProcess();
        process.setStatus(ProcessStatus.PHONE_SUBMITTED);
        process.setPatientPhone("+5492615551234");
        when(processRepository.findByProcessIdAndUserId("process-1", 1L)).thenReturn(Optional.of(process));

        reservationService.submitPhone(user, "process-1", "+549 261 555-1234");

        verify(outboxService, never()).enqueue(any(), any(), any(), any());
    }

    private CatalogProfessionalDTO enabledProfessional() {
        return new CatalogProfessionalDTO(
                15L, "Ana", "Gomez", 3L, "Cardiologia", true, List.of()
        );
    }

    @Test
    void listAppointmentsEnrichesRemoteDataWithLocalProcess() {
        LocalDate from = date;
        LocalDate to = date.plusDays(1);
        when(processRepository.findAllByUserId(1L)).thenReturn(List.of(waitingProcess()));
        when(catedraClient.listAppointments(
                "juan.perez", "CONFIRMED", from, to, 0, 100, null
        )).thenReturn(new AppointmentPage(
                List.of(remoteAppointment("process-1", "CONFIRMED", LocalTime.of(9, 0))),
                1L,
                null
        ));

        AppointmentPageDTO result = reservationService.listAppointments(
                user, "confirmed", from, to, 0, 20, null
        );

        assertThat(result.totalCount()).isEqualTo(1L);
        assertThat(result.link()).isNull();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).localStatus()).isEqualTo("WAITING_FOR_PHONE");
        assertThat(result.content().get(0).requestEventId()).isEqualTo("request-1");
        assertThat(result.content().get(0).expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void listAppointmentsExcludesReservationsWithoutLocalOwnership() {
        when(processRepository.findAllByUserId(1L)).thenReturn(List.of(waitingProcess()));
        when(catedraClient.listAppointments(
                "juan.perez", null, null, null, 0, 100, null
        )).thenReturn(new AppointmentPage(List.of(
                remoteAppointment("process-2", "CONFIRMED", LocalTime.of(10, 0)),
                remoteAppointment("process-1", "PHONE_PENDING", LocalTime.of(9, 0))
        ), 2L, null));

        AppointmentPageDTO result = reservationService.listAppointments(
                user, null, null, null, 0, 20, null
        );

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).reservationProcessId()).isEqualTo("process-1");
        assertThat(result.totalCount()).isEqualTo(1L);
    }

    @Test
    void listAppointmentsPaginatesOwnedAppointmentsLocally() {
        LocalDate from = date;
        LocalDate to = date.plusDays(1);
        when(processRepository.findAllByUserId(1L)).thenReturn(List.of(
                waitingProcess(),
                processWithStatus("process-2", ProcessStatus.CONFIRMED)
        ));
        when(catedraClient.listAppointments(
                "juan.perez", null, from, to, 0, 100, null
        )).thenReturn(new AppointmentPage(List.of(
                remoteAppointment("process-1", "PHONE_PENDING", LocalTime.of(9, 0)),
                remoteAppointment("process-2", "CONFIRMED", LocalTime.of(10, 0))
        ), 2L, null));

        AppointmentPageDTO result = reservationService.listAppointments(
                user, null, from, to, 0, 1, null
        );

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).reservationProcessId()).isEqualTo("process-1");
        assertThat(result.totalCount()).isEqualTo(2L);
        assertThat(result.link()).contains("page=1").contains("size=1").contains("rel=\"next\"");
    }

    @Test
    void listAppointmentsRejectsInvalidStatusAndRange() {
        assertThatThrownBy(() -> reservationService.listAppointments(
                user, "UNKNOWN", date, date, 0, 20, null
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("INVALID_STATUS")
        );

        assertThatThrownBy(() -> reservationService.listAppointments(
                user, null, date.plusDays(1), date, 0, 20, null
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("INVALID_DATE_RANGE")
        );
    }

    @Test
    void listAppointmentsReadsAdditionalPagesWhenTotalCountRequiresIt() {
        when(processRepository.findAllByUserId(1L)).thenReturn(List.of(waitingProcess()));
        when(catedraClient.listAppointments(
                "juan.perez", null, null, null, 0, 100, null
        )).thenReturn(new AppointmentPage(
                List.of(remoteAppointment("process-2", "CONFIRMED", LocalTime.of(10, 0))),
                2L,
                null
        ));
        when(catedraClient.listAppointments(
                "juan.perez", null, null, null, 1, 100, null
        )).thenReturn(new AppointmentPage(
                List.of(remoteAppointment("process-1", "CONFIRMED", LocalTime.of(9, 0))),
                2L,
                null
        ));

        AppointmentPageDTO result = reservationService.listAppointments(
                user, null, null, null, 0, 20, null
        );

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).reservationProcessId()).isEqualTo("process-1");
        assertThat(result.totalCount()).isEqualTo(1L);
    }

    @Test
    void listAppointmentsRejectsInvalidSort() {
        assertThatThrownBy(() -> reservationService.listAppointments(
                user, null, null, null, 0, 20, "unknown,desc"
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("INVALID_SORT")
        );
    }

    @Test
    void cancelAppointmentUpdatesLocalProcess() {
        ReservationProcess process = heldProcess();
        process.setStatus(ProcessStatus.CONFIRMED);
        process.setReservationId(8251L);
        when(processRepository.findByProcessIdAndUserId("process-1", 1L)).thenReturn(Optional.of(process));
        Instant cancelledAt = Instant.now();
        when(catedraClient.cancelAppointment("process-1", "motivo")).thenReturn(new CancelResponseDTO(
                8251L, "process-1", "CANCELLED", cancelledAt, "motivo"
        ));

        CancelResponseDTO response = reservationService.cancelAppointment(user, "process-1", "motivo");

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.CANCELLED);
        assertThat(process.getCancelledAt()).isEqualTo(cancelledAt);
        assertThat(process.getReason()).isEqualTo("motivo");
        verify(catedraClient).cancelAppointment("process-1", "motivo");
    }

    @Test
    void cancelAppointmentIsIdempotentWhenAlreadyCancelled() {
        ReservationProcess process = heldProcess();
        process.setStatus(ProcessStatus.CANCELLED);
        when(processRepository.findByProcessIdAndUserId("process-1", 1L)).thenReturn(Optional.of(process));

        CancelResponseDTO response = reservationService.cancelAppointment(user, "process-1", null);

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(catedraClient, never()).cancelAppointment(any(), any());
    }

    @Test
    void cancelAppointmentRejectsProcessThatIsNotConfirmed() {
        when(processRepository.findByProcessIdAndUserId("process-1", 1L))
                .thenReturn(Optional.of(heldProcess()));

        assertThatThrownBy(() -> reservationService.cancelAppointment(user, "process-1", null))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("RESERVATION_NOT_CANCELLABLE");
                });
        verify(catedraClient, never()).cancelAppointment(any(), any());
    }

    @Test
    void cancelAppointmentReconcilesLostConfirmationBeforeCancelling() {
        ReservationProcess process = waitingProcess();
        when(processRepository.findByProcessIdAndUserId("process-1", 1L)).thenReturn(Optional.of(process));
        when(catedraClient.findAppointment("process-1", "juan.perez")).thenReturn(Optional.of(
                new CatedraAppointmentDTO(
                        8251L, "process-1", "juan.perez", date, LocalTime.of(9, 0), LocalTime.of(9, 30),
                        15L, "Ana", "Gomez", "Juan", "Perez", "+5492615551234",
                        "CONFIRMED", Instant.now(), Instant.now(), null, null
                )
        ));
        Instant cancelledAt = Instant.now();
        when(catedraClient.cancelAppointment("process-1", "motivo")).thenReturn(new CancelResponseDTO(
                8251L, "process-1", "CANCELLED", cancelledAt, "motivo"
        ));

        CancelResponseDTO response = reservationService.cancelAppointment(user, "process-1", "motivo");

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.CANCELLED);
        verify(catedraClient).cancelAppointment("process-1", "motivo");
    }

    @Test
    void cancelAppointmentRejectsTooLongReason() {
        assertThatThrownBy(() -> reservationService.cancelAppointment(user, "process-1", "x".repeat(501)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("VALIDATION_ERROR")
                );
        verify(processRepository, never()).findByProcessIdAndUserId(any(), any());
    }

    @Test
    void cancelAppointmentReconcilesRemoteCancellationAfterTimeout() {
        ReservationProcess process = processWithStatus("process-1", ProcessStatus.CONFIRMED);
        process.setReservationId(8251L);
        Instant cancelledAt = Instant.now();
        when(processRepository.findByProcessIdAndUserId("process-1", 1L)).thenReturn(Optional.of(process));
        when(catedraClient.cancelAppointment("process-1", "motivo"))
                .thenThrow(new ApiException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "CATEDRA_UNAVAILABLE",
                        "sin conexión"
                ));
        when(catedraClient.findAppointment("process-1", "juan.perez")).thenReturn(Optional.of(
                new CatedraAppointmentDTO(
                        8251L, "process-1", "juan.perez", date, LocalTime.of(9, 0), LocalTime.of(9, 30),
                        15L, "Ana", "Gomez", "Juan", "Perez", "+5492615551234",
                        "CANCELLED", Instant.now(), null, cancelledAt, "motivo"
                )
        ));

        CancelResponseDTO response = reservationService.cancelAppointment(user, "process-1", "motivo");

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.cancelledAt()).isEqualTo(cancelledAt);
        assertThat(response.reason()).isEqualTo("motivo");
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.CANCELLED);
        verify(processRepository).save(process);
    }

    private ReservationProcess waitingProcess() {
        ReservationProcess process = heldProcess();
        process.setStatus(ProcessStatus.WAITING_FOR_PHONE);
        process.setRequestEventId("request-1");
        process.setExpiresAt(expiresAt);
        return process;
    }

    private ReservationProcess processWithStatus(String processId, ProcessStatus status) {
        ReservationProcess process = heldProcess();
        process.setProcessId(processId);
        process.setStatus(status);
        return process;
    }

    private CatedraAppointmentDTO remoteAppointment(String processId, String status, LocalTime startTime) {
        return new CatedraAppointmentDTO(
                8251L, processId, "juan.perez", date, startTime, startTime.plusMinutes(30),
                15L, "Ana", "Gomez", "Juan", "Perez", "+5492615551234",
                status, Instant.now(), null, null, null
        );
    }

    private AppointmentHold heldHold(Instant holdExpiresAt) {
        AppointmentHold hold = new AppointmentHold();
        hold.setHoldId("hold-1");
        hold.setProcessId("process-1");
        hold.setUser(user);
        hold.setProfessionalId(15L);
        hold.setStatus("HELD");
        hold.setExpiresAt(holdExpiresAt);
        return hold;
    }

    private ReservationProcess heldProcess() {
        ReservationProcess process = new ReservationProcess();
        process.setProcessId("process-1");
        process.setUser(user);
        process.setHoldId("hold-1");
        process.setStatus(ProcessStatus.HELD);
        return process;
    }
}
