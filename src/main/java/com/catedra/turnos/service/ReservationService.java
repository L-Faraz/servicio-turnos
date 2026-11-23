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
import com.catedra.turnos.dto.catedra.ConfirmHoldRequestDTO;
import com.catedra.turnos.dto.catedra.ConfirmHoldResponseDTO;
import com.catedra.turnos.dto.catedra.HoldRequestDTO;
import com.catedra.turnos.dto.catedra.HoldResponseDTO;
import com.catedra.turnos.repository.AppointmentHoldRepository;
import com.catedra.turnos.repository.ReservationProcessRepository;
import com.catedra.turnos.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
public class ReservationService {

    private final CatalogoClient catalogoClient;
    private final CatedraClient catedraClient;
    private final AvailabilityService availabilityService;
    private final ReservationProcessRepository processRepository;
    private final AppointmentHoldRepository holdRepository;

    public ReservationService(
            CatalogoClient catalogoClient,
            CatedraClient catedraClient,
            AvailabilityService availabilityService,
            ReservationProcessRepository processRepository,
            AppointmentHoldRepository holdRepository
    ) {
        this.catalogoClient = catalogoClient;
        this.catedraClient = catedraClient;
        this.availabilityService = availabilityService;
        this.processRepository = processRepository;
        this.holdRepository = holdRepository;
    }

    @Transactional
    public CreatedHoldResponse createHold(AppUser user, CreateHoldRequest request) {
        CatalogProfessionalDTO professional = catalogoClient.getProfessional(request.professionalId());
        if (!professional.enabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROFESSIONAL_DISABLED", "El profesional está deshabilitado");
        }

        AvailableSlotDTO slot = findAvailableSlot(user, request);
        HoldResponseDTO remote = catedraClient.createHold(new HoldRequestDTO(
                request.professionalId(),
                request.date(),
                request.startTime(),
                user.getLogin()
        ));

        if (!StringUtils.hasText(remote.holdId()) || !StringUtils.hasText(remote.reservationProcessId())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "CATEDRA_ERROR", "Respuesta de hold incompleta");
        }

        Instant now = Instant.now();
        ReservationProcess process = new ReservationProcess();
        process.setProcessId(remote.reservationProcessId());
        process.setUser(user);
        process.setHoldId(remote.holdId());
        process.setProfessionalId(request.professionalId());
        process.setProfessionalFirstName(professional.firstName());
        process.setProfessionalLastName(professional.lastName());
        process.setAppointmentDate(request.date());
        process.setStartTime(request.startTime());
        process.setEndTime(slot.endTime());
        process.setExternalPatientId(user.getLogin());
        process.setStatus(ProcessStatus.HELD);
        process.setExpiresAt(remote.expiresAt());
        process.setCreatedAt(now);
        process.setUpdatedAt(now);

        AppointmentHold hold = new AppointmentHold();
        hold.setHoldId(remote.holdId());
        hold.setProcessId(process.getProcessId());
        hold.setUser(user);
        hold.setProfessionalId(request.professionalId());
        hold.setAppointmentDate(request.date());
        hold.setStartTime(request.startTime());
        hold.setEndTime(slot.endTime());
        hold.setStatus(remote.status() == null ? "HELD" : remote.status());
        hold.setExpiresAt(remote.expiresAt());
        hold.setCreatedAt(now);

        processRepository.save(process);
        holdRepository.save(hold);

        return new CreatedHoldResponse(
                process.getProcessId(),
                hold.getHoldId(),
                hold.getStatus(),
                remote.expiresAt()
        );
    }

    @Transactional
    public ConfirmedHoldResponse confirmHold(AppUser user, String holdId) {
        AppointmentHold hold = holdRepository.findByHoldIdAndUserId(holdId, user.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "HOLD_NOT_FOUND",
                        "El hold no existe o no pertenece al usuario"
                ));

        if (!"HELD".equalsIgnoreCase(hold.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "HOLD_ALREADY_CONFIRMED", "El hold ya fue confirmado o cerrado");
        }
        if (hold.getExpiresAt() != null && hold.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "HOLD_EXPIRED", "El hold venció");
        }

        ReservationProcess process = processRepository.findByHoldIdAndStatus(holdId, ProcessStatus.HELD)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "PROCESS_NOT_IN_HELD_STATUS",
                        "El proceso local no está en estado HELD"
                ));

        ConfirmHoldResponseDTO remote = catedraClient.confirmHold(holdId, new ConfirmHoldRequestDTO(
                user.getLogin(),
                user.getFirstName(),
                user.getLastName()
        ));

        Instant now = Instant.now();
        process.setStatus(ProcessStatus.WAITING_FOR_PHONE);
        process.setExpiresAt(remote.expiresAt());
        process.setUpdatedAt(now);
        hold.setStatus("CONFIRMED");

        processRepository.save(process);
        holdRepository.save(hold);

        return new ConfirmedHoldResponse(
                process.getProcessId(),
                process.getStatus().name(),
                remote.expiresAt()
        );
    }

    private AvailableSlotDTO findAvailableSlot(AppUser user, CreateHoldRequest request) {
        List<AvailableSlotDTO> slots = availabilityService.getAvailability(
                user,
                request.professionalId(),
                request.date(),
                request.date()
        );

        AvailableSlotDTO slot = slots.stream()
                .filter(candidate -> candidate.startTime().equals(request.startTime()))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_SLOT",
                        "El slot no corresponde a un horario vigente"
                ));

        if (!slot.available()) {
            if ("PASADO".equals(slot.reason())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SLOT", "El horario ya pasó");
            }
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_ALREADY_RESERVED", "El horario no está disponible");
        }

        return slot;
    }
}
