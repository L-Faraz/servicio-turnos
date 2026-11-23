package com.catedra.turnos.service;

import com.catedra.turnos.client.CatalogoClient;
import com.catedra.turnos.client.CatedraClient;
import com.catedra.turnos.domain.AppointmentHold;
import com.catedra.turnos.domain.AppUser;
import com.catedra.turnos.domain.ReservationProcess;
import com.catedra.turnos.domain.enums.ProcessStatus;
import com.catedra.turnos.dto.AvailableSlotDTO;
import com.catedra.turnos.dto.ConfirmedHoldResponse;
import com.catedra.turnos.dto.CreateHoldRequest;
import com.catedra.turnos.dto.CreatedHoldResponse;
import com.catedra.turnos.dto.catalogo.CatalogProfessionalDTO;
import com.catedra.turnos.dto.catalogo.WeeklyScheduleDTO;
import com.catedra.turnos.dto.catedra.ConfirmHoldResponseDTO;
import com.catedra.turnos.dto.catedra.HoldResponseDTO;
import com.catedra.turnos.repository.AppointmentHoldRepository;
import com.catedra.turnos.repository.ReservationProcessRepository;
import com.catedra.turnos.web.ApiException;
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

    private ReservationService reservationService;
    private AppUser user;
    private LocalDate date;
    private Instant expiresAt;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
                catalogoClient,
                catedraClient,
                availabilityService,
                processRepository,
                holdRepository
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
    void confirmHoldMovesProcessToWaitingForPhone() {
        AppointmentHold hold = heldHold(expiresAt);
        ReservationProcess process = heldProcess();
        when(holdRepository.findByHoldIdAndUserId("hold-1", 1L)).thenReturn(Optional.of(hold));
        when(processRepository.findByHoldIdAndStatus("hold-1", ProcessStatus.HELD)).thenReturn(Optional.of(process));
        when(catedraClient.confirmHold(eq("hold-1"), any())).thenReturn(
                new ConfirmHoldResponseDTO("process-1", "WAITING_FOR_PHONE", expiresAt)
        );

        ConfirmedHoldResponse response = reservationService.confirmHold(user, "hold-1");

        assertThat(response.status()).isEqualTo("WAITING_FOR_PHONE");
        assertThat(process.getStatus()).isEqualTo(ProcessStatus.WAITING_FOR_PHONE);
        assertThat(hold.getStatus()).isEqualTo("CONFIRMED");
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

        assertThatThrownBy(() -> reservationService.confirmHold(user, "hold-1"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("HOLD_EXPIRED")
                );
    }

    private AppointmentHold heldHold(Instant holdExpiresAt) {
        AppointmentHold hold = new AppointmentHold();
        hold.setHoldId("hold-1");
        hold.setProcessId("process-1");
        hold.setUser(user);
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
