package com.catedra.turnos.service;

import com.catedra.turnos.client.CatalogoClient;
import com.catedra.turnos.client.CatedraClient;
import com.catedra.turnos.config.CatedraProperties;
import com.catedra.turnos.domain.AppointmentHold;
import com.catedra.turnos.domain.AppUser;
import com.catedra.turnos.domain.ReservationProcess;
import com.catedra.turnos.domain.enums.ProcessStatus;
import com.catedra.turnos.dto.AdditionalInformationSubmittedEvent;
import com.catedra.turnos.dto.AvailableSlotDTO;
import com.catedra.turnos.dto.ConfirmedHoldResponse;
import com.catedra.turnos.dto.CreateHoldRequest;
import com.catedra.turnos.dto.CreatedHoldResponse;
import com.catedra.turnos.dto.catalogo.CatalogProfessionalDTO;
import com.catedra.turnos.dto.catedra.CatedraAppointmentDTO;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReservationService {

    private static final EnumSet<ProcessStatus> ACTIVE_HOLD_STATUSES = EnumSet.of(
            ProcessStatus.HELD,
            ProcessStatus.WAITING_FOR_PHONE,
            ProcessStatus.PHONE_SUBMITTED
    );

    private final CatalogoClient catalogoClient;
    private final CatedraClient catedraClient;
    private final AvailabilityService availabilityService;
    private final ReservationProcessRepository processRepository;
    private final AppointmentHoldRepository holdRepository;
    private final OutboxService outboxService;
    private final CatedraProperties properties;

    public ReservationService(
            CatalogoClient catalogoClient,
            CatedraClient catedraClient,
            AvailabilityService availabilityService,
            ReservationProcessRepository processRepository,
            AppointmentHoldRepository holdRepository,
            OutboxService outboxService,
            CatedraProperties properties
    ) {
        this.catalogoClient = catalogoClient;
        this.catedraClient = catedraClient;
        this.availabilityService = availabilityService;
        this.processRepository = processRepository;
        this.holdRepository = holdRepository;
        this.outboxService = outboxService;
        this.properties = properties;
    }

    @Transactional
    public CreatedHoldResponse createHold(AppUser user, CreateHoldRequest request) {
        CatalogProfessionalDTO professional = catalogoClient.getProfessional(request.professionalId());
        validateProfessional(professional);

        AvailableSlotDTO slot = findAvailableSlot(user, request);
        Optional<ReservationProcess> existing = findActiveProcess(user, request);
        if (existing.isPresent()) {
            return toCreatedHoldResponse(existing.get());
        }

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
        process.setPhoneAttempt(0);

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

        ReservationProcess process = processRepository.findByHoldIdAndUserId(holdId, user.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROCESS_NOT_FOUND",
                        "No existe el proceso del hold"
                ));

        CatalogProfessionalDTO professional = catalogoClient.getProfessional(hold.getProfessionalId());
        validateProfessional(professional);

        if (process.getStatus() != ProcessStatus.HELD || "CONFIRMED".equalsIgnoreCase(hold.getStatus())) {
            return toConfirmedHoldResponse(process);
        }
        if (hold.getExpiresAt() != null && hold.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "HOLD_EXPIRED", "El hold venció");
        }

        ConfirmHoldResponseDTO remote;
        try {
            remote = catedraClient.confirmHold(holdId, new ConfirmHoldRequestDTO(
                    user.getLogin(),
                    user.getFirstName(),
                    user.getLastName()
            ));
        } catch (ApiException exception) {
            if ("RESERVATION_PROCESS_ALREADY_CONFIRMED".equals(exception.getCode())) {
                return reconcileConfirmation(user, process, hold);
            }
            if ("CATEDRA_UNAVAILABLE".equals(exception.getCode())
                    || "CATEDRA_ERROR".equals(exception.getCode())) {
                ConfirmedHoldResponse reconciled = reconcileIfRemoteExists(user, process, hold);
                if (reconciled != null) {
                    return reconciled;
                }
            }
            throw exception;
        }

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

    @Transactional
    public void submitPhone(AppUser user, String processId, String phoneNumber) {
        ReservationProcess process = processRepository.findByProcessIdAndUserId(processId, user.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROCESS_NOT_FOUND",
                        "No existe el proceso"
                ));

        String phone = normalizePhone(phoneNumber);
        if (!phone.matches("^\\+?[0-9]{7,15}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHONE_NUMBER", "El teléfono no es válido");
        }

        if (process.getStatus() == ProcessStatus.PHONE_SUBMITTED) {
            if (phone.equals(process.getPatientPhone())) {
                return;
            }
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_PROCESS_STATE",
                    "El teléfono ya fue enviado para este ciclo"
            );
        }
        if (process.getStatus() != ProcessStatus.WAITING_FOR_PHONE) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_PROCESS_STATE", "El proceso no espera teléfono");
        }
        if (!StringUtils.hasText(process.getRequestEventId())) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_EVENT_MISSING", "Falta la solicitud de teléfono");
        }
        if (process.getExpiresAt() != null && process.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.GONE, "PROCESS_EXPIRED", "El proceso venció");
        }

        String eventId = deterministicEventId(
                process.getProcessId(),
                process.getRequestEventId(),
                phone,
                process.getPhoneAttempt()
        );
        AdditionalInformationSubmittedEvent event = new AdditionalInformationSubmittedEvent(
                eventId,
                "AdditionalInformationSubmitted",
                properties.getEffectiveGroupId(),
                process.getProcessId(),
                process.getHoldId(),
                process.getRequestEventId(),
                Map.of("phoneNumber", phone),
                Instant.now(),
                1
        );

        outboxService.enqueue(
                eventId,
                properties.getEffectivePhoneTopic(),
                process.getProcessId(),
                event
        );

        process.setStatus(ProcessStatus.PHONE_SUBMITTED);
        process.setPatientPhone(phone);
        process.setReason(null);
        process.setUpdatedAt(Instant.now());
        processRepository.save(process);
    }

    private void validateProfessional(CatalogProfessionalDTO professional) {
        if (professional == null || !professional.enabled()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROFESSIONAL_DISABLED",
                    "El profesional está deshabilitado"
            );
        }
    }

    private Optional<ReservationProcess> findActiveProcess(AppUser user, CreateHoldRequest request) {
        List<ReservationProcess> processes = processRepository
                .findByUserIdAndProfessionalIdAndAppointmentDateAndStartTimeAndStatusIn(
                        user.getId(),
                        request.professionalId(),
                        request.date(),
                        request.startTime(),
                        ACTIVE_HOLD_STATUSES
                );

        return processes.stream()
                .filter(process -> process.getExpiresAt() == null || process.getExpiresAt().isAfter(Instant.now()))
                .findFirst();
    }

    private CreatedHoldResponse toCreatedHoldResponse(ReservationProcess process) {
        String holdStatus = holdRepository.findByHoldId(process.getHoldId())
                .map(AppointmentHold::getStatus)
                .orElse(process.getStatus().name());
        return new CreatedHoldResponse(
                process.getProcessId(),
                process.getHoldId(),
                holdStatus,
                process.getExpiresAt()
        );
    }

    private ConfirmedHoldResponse toConfirmedHoldResponse(ReservationProcess process) {
        return new ConfirmedHoldResponse(
                process.getProcessId(),
                process.getStatus().name(),
                process.getExpiresAt()
        );
    }

    private ConfirmedHoldResponse reconcileConfirmation(
            AppUser user,
            ReservationProcess process,
            AppointmentHold hold
    ) {
        ConfirmedHoldResponse reconciled = reconcileIfRemoteExists(user, process, hold);
        if (reconciled != null) {
            return reconciled;
        }
        process.setStatus(ProcessStatus.WAITING_FOR_PHONE);
        process.setUpdatedAt(Instant.now());
        hold.setStatus("CONFIRMED");
        processRepository.save(process);
        holdRepository.save(hold);
        return toConfirmedHoldResponse(process);
    }

    private ConfirmedHoldResponse reconcileIfRemoteExists(
            AppUser user,
            ReservationProcess process,
            AppointmentHold hold
    ) {
        Optional<CatedraAppointmentDTO> remote =
                catedraClient.findAppointment(process.getProcessId(), user.getLogin());
        if (remote.isEmpty()) {
            return null;
        }

        ProcessStatus status = mapRemoteStatus(remote.get().status());
        process.setStatus(status);
        process.setReservationId(remote.get().reservationId());
        process.setUpdatedAt(Instant.now());
        hold.setStatus("CONFIRMED");
        processRepository.save(process);
        holdRepository.save(hold);
        return toConfirmedHoldResponse(process);
    }

    private ProcessStatus mapRemoteStatus(String status) {
        if (status == null) {
            return ProcessStatus.WAITING_FOR_PHONE;
        }
        return switch (status.trim().toUpperCase()) {
            case "PHONE_PENDING" -> ProcessStatus.WAITING_FOR_PHONE;
            case "CONFIRMED" -> ProcessStatus.CONFIRMED;
            case "CANCELLED" -> ProcessStatus.CANCELLED;
            case "FAILED" -> ProcessStatus.FAILED;
            default -> ProcessStatus.WAITING_FOR_PHONE;
        };
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

    private String normalizePhone(String phoneNumber) {
        return phoneNumber == null ? "" : phoneNumber.replaceAll("[\\s\\-()\\.]", "");
    }

    private String deterministicEventId(
            String processId,
            String requestEventId,
            String phone,
            int phoneAttempt
    ) {
        String seed = processId + "|" + requestEventId + "|" + phone + "|" + phoneAttempt;
        return "phone-" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
