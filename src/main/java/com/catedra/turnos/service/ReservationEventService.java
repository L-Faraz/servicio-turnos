package com.catedra.turnos.service;

import com.catedra.turnos.domain.ReservationProcess;
import com.catedra.turnos.domain.enums.ProcessStatus;
import com.catedra.turnos.dto.TurnosEventDTO;
import com.catedra.turnos.repository.ReservationProcessRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
public class ReservationEventService {

    private final ReservationProcessRepository processRepository;
    private final MeterRegistry meterRegistry;

    public ReservationEventService(
            ReservationProcessRepository processRepository,
            MeterRegistry meterRegistry
    ) {
        this.processRepository = processRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void handle(TurnosEventDTO event) {
        switch (event.eventType()) {
            case "AdditionalInformationRequested" -> handleRequested(event);
            case "AppointmentConfirmed" -> handleConfirmed(event);
            case "AdditionalInformationRejected" -> handleRejected(event);
            case "AppointmentProcessExpired" -> handleExpired(event);
            case "AppointmentProcessInvalid" -> handleInvalid(event);
            case "AppointmentCancelled" -> handleCancelled(event);
            default -> meterRegistry.counter("turnos.kafka.events", "outcome", "ignored",
                    "type", event.eventType()).increment();
        }
    }

    private void handleRequested(TurnosEventDTO event) {
        if (StringUtils.hasText(event.requiredField())
                && !"PHONE_NUMBER".equalsIgnoreCase(event.requiredField())) {
            throw new IllegalArgumentException("requiredField no soportado: " + event.requiredField());
        }

        ReservationProcess process = findProcess(event);
        if (process.getStatus().isFinal()) {
            countLate(event);
            return;
        }

        boolean sameRequest = event.eventId().equals(process.getRequestEventId());
        process.setStatus(ProcessStatus.WAITING_FOR_PHONE);
        process.setRequestEventId(event.eventId());
        if (!sameRequest) {
            process.setPatientPhone(null);
        }
        process.setExpiresAt(event.expiresAt());
        process.setReason(null);
        process.setUpdatedAt(Instant.now());
        processRepository.save(process);
    }

    private void handleConfirmed(TurnosEventDTO event) {
        ReservationProcess process = findProcess(event);
        if (isLockedFinal(process, ProcessStatus.CONFIRMED, event)) {
            return;
        }

        process.setStatus(ProcessStatus.CONFIRMED);
        if (event.reservationId() != null) {
            process.setReservationId(event.reservationId());
        }
        process.setReason(null);
        process.setUpdatedAt(Instant.now());
        processRepository.save(process);
    }

    private void handleRejected(TurnosEventDTO event) {
        ReservationProcess process = findProcess(event);
        if (isLockedFinal(process, null, event)) {
            return;
        }

        process.setStatus(ProcessStatus.WAITING_FOR_PHONE);
        process.setReason(StringUtils.hasText(event.reason()) ? event.reason() : event.message());
        process.setPatientPhone(null);
        process.setExpiresAt(event.expiresAt());
        process.setPhoneAttempt(process.getPhoneAttempt() + 1);
        process.setUpdatedAt(Instant.now());
        processRepository.save(process);
    }

    private void handleExpired(TurnosEventDTO event) {
        ReservationProcess process = findProcess(event);
        if (isLockedFinal(process, ProcessStatus.EXPIRED, event)) {
            return;
        }

        process.setStatus(ProcessStatus.EXPIRED);
        process.setReason(StringUtils.hasText(event.reason()) ? event.reason() : "HOLD_EXPIRED");
        process.setUpdatedAt(Instant.now());
        processRepository.save(process);
    }

    private void handleInvalid(TurnosEventDTO event) {
        ReservationProcess process = findProcess(event);
        if (isLockedFinal(process, ProcessStatus.INVALID, event)) {
            return;
        }

        process.setStatus(ProcessStatus.INVALID);
        process.setReason(StringUtils.hasText(event.reason()) ? event.reason() : "REQUEST_EVENT_MISMATCH");
        process.setUpdatedAt(Instant.now());
        processRepository.save(process);
    }

    private void handleCancelled(TurnosEventDTO event) {
        ReservationProcess process = findProcess(event);
        if (process.getStatus().isFinal()
                && process.getStatus() != ProcessStatus.CANCELLED
                && process.getStatus() != ProcessStatus.CONFIRMED) {
            countLate(event);
            return;
        }

        process.setStatus(ProcessStatus.CANCELLED);
        process.setReason(event.reason());
        process.setCancelledAt(event.occurredAt());
        if (event.reservationId() != null) {
            process.setReservationId(event.reservationId());
        }
        process.setUpdatedAt(Instant.now());
        processRepository.save(process);
    }

    private boolean isLockedFinal(ReservationProcess process, ProcessStatus allowedFinal, TurnosEventDTO event) {
        if (!process.getStatus().isFinal()) {
            return false;
        }
        if (allowedFinal != null && process.getStatus() == allowedFinal) {
            return false;
        }
        countLate(event);
        return true;
    }

    private void countLate(TurnosEventDTO event) {
        meterRegistry.counter("turnos.kafka.events", "outcome", "late_final", "type", event.eventType())
                .increment();
    }

    private ReservationProcess findProcess(TurnosEventDTO event) {
        return processRepository.findById(event.reservationProcessId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el proceso " + event.reservationProcessId()
                ));
    }
}
