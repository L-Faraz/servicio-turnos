package com.catedra.turnos.service;

import com.catedra.turnos.client.CatalogoClient;
import com.catedra.turnos.client.CatedraClient;
import com.catedra.turnos.config.CatedraProperties;
import com.catedra.turnos.domain.AppointmentHold;
import com.catedra.turnos.domain.AppUser;
import com.catedra.turnos.domain.ReservationProcess;
import com.catedra.turnos.domain.enums.ProcessStatus;
import com.catedra.turnos.dto.AdditionalInformationSubmittedEvent;
import com.catedra.turnos.dto.AppointmentDTO;
import com.catedra.turnos.dto.AppointmentPageDTO;
import com.catedra.turnos.dto.AvailableSlotDTO;
import com.catedra.turnos.dto.ConfirmedHoldResponse;
import com.catedra.turnos.dto.CreateHoldRequest;
import com.catedra.turnos.dto.CreatedHoldResponse;
import com.catedra.turnos.dto.catalogo.CatalogProfessionalDTO;
import com.catedra.turnos.dto.catedra.AppointmentPage;
import com.catedra.turnos.dto.catedra.CancelResponseDTO;
import com.catedra.turnos.dto.catedra.CatedraAppointmentDTO;
import com.catedra.turnos.dto.catedra.ConfirmHoldRequestDTO;
import com.catedra.turnos.dto.catedra.ConfirmHoldResponseDTO;
import com.catedra.turnos.dto.catedra.HoldRequestDTO;
import com.catedra.turnos.dto.catedra.HoldResponseDTO;
import com.catedra.turnos.repository.AppointmentHoldRepository;
import com.catedra.turnos.repository.ReservationProcessRepository;
import com.catedra.turnos.web.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ReservationService {

    private static final EnumSet<ProcessStatus> ACTIVE_HOLD_STATUSES = EnumSet.of(
            ProcessStatus.HELD,
            ProcessStatus.WAITING_FOR_PHONE,
            ProcessStatus.PHONE_SUBMITTED
    );

    private static final Set<String> REMOTE_STATUSES = Set.of(
            "PHONE_PENDING", "CONFIRMED", "CANCELLED", "FAILED"
    );

    private static final int REMOTE_APPOINTMENT_PAGE_SIZE = 100;
    private static final int MAX_REMOTE_APPOINTMENT_PAGES = 101;
    private static final String APPOINTMENTS_PATH = "/api/appointments";

    private static final EnumSet<ProcessStatus> STALE_CANCEL_STATUSES = EnumSet.of(
            ProcessStatus.WAITING_FOR_PHONE,
            ProcessStatus.PHONE_SUBMITTED
    );

    private static final Set<String> CANCEL_RECONCILIABLE_ERRORS = Set.of(
            "CATEDRA_UNAVAILABLE",
            "CATEDRA_ERROR",
            "RESERVATION_NOT_CANCELLABLE"
    );

    private static final Pattern SORT_PATTERN = Pattern.compile(
            "^(appointmentDate|startTime|professionalId|status),(asc|desc)$"
    );

    private final CatalogoClient catalogoClient;
    private final CatedraClient catedraClient;
    private final AvailabilityService availabilityService;
    private final ReservationProcessRepository processRepository;
    private final AppointmentHoldRepository holdRepository;
    private final OutboxService outboxService;
    private final CatedraProperties properties;
    private final MeterRegistry meterRegistry;

    public ReservationService(
            CatalogoClient catalogoClient,
            CatedraClient catedraClient,
            AvailabilityService availabilityService,
            ReservationProcessRepository processRepository,
            AppointmentHoldRepository holdRepository,
            OutboxService outboxService,
            CatedraProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.catalogoClient = catalogoClient;
        this.catedraClient = catedraClient;
        this.availabilityService = availabilityService;
        this.processRepository = processRepository;
        this.holdRepository = holdRepository;
        this.outboxService = outboxService;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
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

    @Transactional(readOnly = true)
    public AppointmentPageDTO listAppointments(
            AppUser user,
            String status,
            LocalDate from,
            LocalDate to,
            int page,
            int size,
            String sort
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "El rango de fechas es inválido");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Paginación inválida");
        }

        String normalizedStatus = normalizeAppointmentStatus(status);
        String effectiveSort = normalizeAppointmentSort(sort);

        Map<String, ReservationProcess> ownedProcesses = processRepository.findAllByUserId(user.getId()).stream()
                .collect(Collectors.toMap(
                        ReservationProcess::getProcessId,
                        Function.identity(),
                        (first, second) -> first
                ));

        List<AppointmentDTO> ownedAppointments = fetchAppointments(
                user,
                normalizedStatus,
                from,
                to,
                effectiveSort
        ).stream()
                .filter(appointment -> appointment != null
                        && ownedProcesses.containsKey(appointment.reservationProcessId())
                        && matchesAppointmentFilters(appointment, normalizedStatus, from, to))
                .map(appointment -> toAppointmentDTO(
                        ownedProcesses.get(appointment.reservationProcessId()),
                        appointment
                ))
                .toList();

        long total = ownedAppointments.size();
        long offset = (long) page * size;
        int fromIndex = (int) Math.min(offset, total);
        int toIndex = (int) Math.min(offset + size, total);
        List<AppointmentDTO> content = ownedAppointments.subList(fromIndex, toIndex);

        meterRegistry.counter("turnos.appointments.list", "outcome", "ok").increment();
        return new AppointmentPageDTO(
                content,
                total,
                buildAppointmentsLink(normalizedStatus, from, to, page, size, effectiveSort, total)
        );
    }

    private List<CatedraAppointmentDTO> fetchAppointments(
            AppUser user,
            String status,
            LocalDate from,
            LocalDate to,
            String sort
    ) {
        List<CatedraAppointmentDTO> all = new ArrayList<>();
        Long remoteTotal = null;
        boolean exhausted = false;

        for (int remotePage = 0; remotePage < MAX_REMOTE_APPOINTMENT_PAGES; remotePage++) {
            AppointmentPage remote = catedraClient.listAppointments(
                    user.getLogin(),
                    status,
                    from,
                    to,
                    remotePage,
                    REMOTE_APPOINTMENT_PAGE_SIZE,
                    sort
            );
            List<CatedraAppointmentDTO> content = remote.content() == null ? List.of() : remote.content();
            if (remotePage == 0) {
                remoteTotal = remote.totalCount();
            }
            all.addAll(content);

            boolean hasNext = hasNextLink(remote.link());
            if (content.isEmpty()
                    || (remoteTotal != null && all.size() >= remoteTotal && !hasNext)
                    || (remoteTotal == null && !hasNext && content.size() < REMOTE_APPOINTMENT_PAGE_SIZE)) {
                exhausted = true;
                break;
            }
        }

        if (!exhausted) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "CATEDRA_PAGINATION_LIMIT",
                    "No se pudo completar el listado de reservas"
            );
        }
        return all;
    }

    private String normalizeAppointmentStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!REMOTE_STATUSES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Estado no soportado");
        }
        return normalized;
    }

    private String normalizeAppointmentSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            return null;
        }
        String normalized = sort.trim();
        if (!SORT_PATTERN.matcher(normalized).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SORT", "Orden no soportado");
        }
        return normalized;
    }

    private boolean hasNextLink(String link) {
        if (!StringUtils.hasText(link)) {
            return false;
        }
        return link.contains("rel=\"next\"") || link.contains("rel=next");
    }

    private boolean matchesAppointmentFilters(
            CatedraAppointmentDTO appointment,
            String status,
            LocalDate from,
            LocalDate to
    ) {
        if (status != null && !status.equalsIgnoreCase(appointment.status())) {
            return false;
        }
        if (from != null
                && (appointment.appointmentDate() == null || appointment.appointmentDate().isBefore(from))) {
            return false;
        }
        return to == null
                || (appointment.appointmentDate() != null && !appointment.appointmentDate().isAfter(to));
    }

    private String buildAppointmentsLink(
            String status,
            LocalDate from,
            LocalDate to,
            int page,
            int size,
            String sort,
            long total
    ) {
        List<String> params = new ArrayList<>();
        if (StringUtils.hasText(status)) {
            params.add("status=" + encode(status));
        }
        if (from != null) {
            params.add("from=" + from);
        }
        if (to != null) {
            params.add("to=" + to);
        }
        if (StringUtils.hasText(sort)) {
            params.add("sort=" + encode(sort));
        }

        List<String> links = new ArrayList<>();
        long lastPage = total == 0 ? 0 : (total - 1) / size;
        if (page > 0 && page - 1 <= lastPage) {
            links.add(appointmentsLink(page - 1, size, params, "prev"));
        }
        if (page < lastPage) {
            links.add(appointmentsLink(page + 1, size, params, "next"));
        }
        return links.isEmpty() ? null : String.join(", ", links);
    }

    private String appointmentsLink(int page, int size, List<String> params, String rel) {
        List<String> query = new ArrayList<>(params);
        query.add("page=" + page);
        query.add("size=" + size);
        return "<" + APPOINTMENTS_PATH + "?" + String.join("&", query) + ">; rel=\"" + rel + "\"";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Transactional
    public CancelResponseDTO cancelAppointment(AppUser user, String processId, String reason) {
        if (reason != null && reason.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "El motivo es demasiado largo");
        }

        ReservationProcess process = processRepository.findByProcessIdAndUserId(processId, user.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROCESS_NOT_FOUND",
                        "No existe el proceso"
                ));

        if (process.getStatus() == ProcessStatus.CANCELLED) {
            return toCancelResponse(process);
        }
        if (STALE_CANCEL_STATUSES.contains(process.getStatus())) {
            CancelResponseDTO reconciled = reconcileStaleStatusBeforeCancel(user, process, reason);
            if (reconciled != null) {
                return reconciled;
            }
        }
        if (process.getStatus() != ProcessStatus.CONFIRMED) {
            meterRegistry.counter("turnos.appointments.cancel", "outcome", "invalid_state").increment();
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "RESERVATION_NOT_CANCELLABLE",
                    "Sólo se pueden cancelar reservas confirmadas"
            );
        }

        CancelResponseDTO remote;
        try {
            remote = catedraClient.cancelAppointment(processId, reason);
        } catch (ApiException exception) {
            if (CANCEL_RECONCILIABLE_ERRORS.contains(exception.getCode())) {
                CancelResponseDTO reconciled = reconcileCancelledRemotely(user, process, reason);
                if (reconciled != null) {
                    return reconciled;
                }
            }
            meterRegistry.counter("turnos.appointments.cancel", "outcome", exception.getCode()).increment();
            throw exception;
        }

        CancelResponseDTO response = applyCancellation(process, remote, reason);
        meterRegistry.counter("turnos.appointments.cancel", "outcome", "ok").increment();
        return response;
    }

    private CancelResponseDTO reconcileStaleStatusBeforeCancel(
            AppUser user,
            ReservationProcess process,
            String reason
    ) {
        Optional<CatedraAppointmentDTO> remote = findRemoteAppointment(user, process);
        if (remote.isEmpty()) {
            return null;
        }
        CatedraAppointmentDTO appointment = remote.get();
        if ("CANCELLED".equalsIgnoreCase(appointment.status())) {
            CancelResponseDTO applied = applyCancellation(
                    process,
                    cancelledResponseFrom(appointment, process.getProcessId(), reason),
                    reason
            );
            meterRegistry.counter("turnos.appointments.cancel", "outcome", "reconciled").increment();
            return applied;
        }
        if ("CONFIRMED".equalsIgnoreCase(appointment.status())) {
            process.setStatus(ProcessStatus.CONFIRMED);
            process.setReason(null);
            process.setUpdatedAt(Instant.now());
            if (appointment.reservationId() != null) {
                process.setReservationId(appointment.reservationId());
            }
            processRepository.save(process);
            meterRegistry.counter("turnos.appointments.cancel", "outcome", "reconciled_confirmation").increment();
        }
        return null;
    }

    private CancelResponseDTO reconcileCancelledRemotely(
            AppUser user,
            ReservationProcess process,
            String reason
    ) {
        Optional<CatedraAppointmentDTO> remote = findRemoteAppointment(user, process);
        if (remote.isEmpty() || !"CANCELLED".equalsIgnoreCase(remote.get().status())) {
            return null;
        }
        CancelResponseDTO applied = applyCancellation(
                process,
                cancelledResponseFrom(remote.get(), process.getProcessId(), reason),
                reason
        );
        meterRegistry.counter("turnos.appointments.cancel", "outcome", "reconciled").increment();
        return applied;
    }

    private Optional<CatedraAppointmentDTO> findRemoteAppointment(
            AppUser user,
            ReservationProcess process
    ) {
        try {
            return catedraClient.findAppointment(process.getProcessId(), user.getLogin());
        } catch (ApiException ignored) {
            return Optional.empty();
        }
    }

    private CancelResponseDTO cancelledResponseFrom(
            CatedraAppointmentDTO remote,
            String processId,
            String reason
    ) {
        return new CancelResponseDTO(
                remote.reservationId(),
                processId,
                "CANCELLED",
                remote.cancelledAt(),
                StringUtils.hasText(remote.cancellationReason()) ? remote.cancellationReason() : reason
        );
    }

    private CancelResponseDTO applyCancellation(
            ReservationProcess process,
            CancelResponseDTO remote,
            String reason
    ) {
        if (!"CANCELLED".equalsIgnoreCase(remote.status())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "CATEDRA_ERROR", "La reserva no quedó cancelada");
        }

        Instant now = Instant.now();
        process.setStatus(ProcessStatus.CANCELLED);
        if (remote.reservationId() != null) {
            process.setReservationId(remote.reservationId());
        }
        process.setCancelledAt(remote.cancelledAt() == null ? now : remote.cancelledAt());
        process.setReason(StringUtils.hasText(remote.reason()) ? remote.reason() : reason);
        process.setUpdatedAt(now);
        processRepository.save(process);

        holdRepository.findByHoldId(process.getHoldId()).ifPresent(hold -> {
            hold.setStatus("CANCELLED");
            holdRepository.save(hold);
        });

        return new CancelResponseDTO(
                process.getReservationId(),
                process.getProcessId(),
                "CANCELLED",
                process.getCancelledAt(),
                process.getReason()
        );
    }

    private AppointmentDTO toAppointmentDTO(ReservationProcess local, CatedraAppointmentDTO appointment) {
        return new AppointmentDTO(
                appointment.reservationId(),
                appointment.reservationProcessId(),
                appointment.externalPatientId(),
                appointment.appointmentDate(),
                appointment.startTime(),
                appointment.endTime(),
                appointment.professionalId(),
                appointment.professionalFirstName(),
                appointment.professionalLastName(),
                appointment.patientFirstName(),
                appointment.patientLastName(),
                appointment.patientPhone(),
                appointment.status(),
                appointment.createdAt(),
                appointment.confirmedAt(),
                appointment.cancelledAt(),
                appointment.cancellationReason(),
                local.getStatus().name(),
                local.getRequestEventId(),
                local.getExpiresAt()
        );
    }

    private CancelResponseDTO toCancelResponse(ReservationProcess process) {
        return new CancelResponseDTO(
                process.getReservationId(),
                process.getProcessId(),
                ProcessStatus.CANCELLED.name(),
                process.getCancelledAt(),
                process.getReason()
        );
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
            if ("HELD".equals(slot.reason())) {
                throw new ApiException(HttpStatus.CONFLICT, "SLOT_ALREADY_HELD", "El horario tiene un bloqueo activo");
            }
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_ALREADY_RESERVED", "El horario ya está reservado");
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
