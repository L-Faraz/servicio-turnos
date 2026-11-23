package com.catedra.turnos.service;

import com.catedra.turnos.client.CatedraClient;
import com.catedra.turnos.config.TurnosProperties;
import com.catedra.turnos.domain.ReservationProcess;
import com.catedra.turnos.domain.enums.ProcessStatus;
import com.catedra.turnos.dto.catedra.CatedraAppointmentDTO;
import com.catedra.turnos.repository.ReservationProcessRepository;
import com.catedra.turnos.web.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "turnos.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class RecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);

    private static final EnumSet<ProcessStatus> EXPIRABLE_STATUSES = EnumSet.of(
            ProcessStatus.HELD,
            ProcessStatus.WAITING_FOR_PHONE,
            ProcessStatus.PHONE_SUBMITTED
    );

    private static final EnumSet<ProcessStatus> RECONCILIABLE_STATUSES = EnumSet.of(
            ProcessStatus.WAITING_FOR_PHONE,
            ProcessStatus.PHONE_SUBMITTED,
            ProcessStatus.CONFIRMED
    );

    private final ReservationProcessRepository processRepository;
    private final CatedraClient catedraClient;
    private final TurnosProperties properties;
    private final MeterRegistry meterRegistry;

    public RecoveryService(
            ReservationProcessRepository processRepository,
            CatedraClient catedraClient,
            TurnosProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.processRepository = processRepository;
        this.catedraClient = catedraClient;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(
            fixedDelayString = "#{@turnosProperties.recovery.intervalMs}",
            initialDelayString = "#{@turnosProperties.recovery.initialDelayMs}"
    )
    @Transactional
    public void run() {
        int expired = expireLocalProcesses();
        int reconciled = reconcileStaleProcesses();
        if (expired > 0 || reconciled > 0) {
            log.info("Recuperación: {} expirados, {} reconciliados", expired, reconciled);
        }
    }

    @Transactional
    public int expireLocalProcesses() {
        Instant now = Instant.now();
        List<ReservationProcess> candidates =
                processRepository.findAllByStatusInAndExpiresAtBefore(EXPIRABLE_STATUSES, now);

        int count = 0;
        for (ReservationProcess process : candidates) {
            if (process.getStatus().isFinal()) {
                continue;
            }
            process.setStatus(ProcessStatus.EXPIRED);
            process.setReason("HOLD_EXPIRED");
            process.setUpdatedAt(now);
            processRepository.save(process);
            meterRegistry.counter("turnos.recovery.processes", "outcome", "expired").increment();
            count++;
        }
        return count;
    }

    @Transactional
    public int reconcileStaleProcesses() {
        Instant threshold = Instant.now().minusMillis(properties.getRecovery().getStaleAfterMs());
        List<ReservationProcess> candidates =
                processRepository.findAllByStatusInAndUpdatedAtBefore(RECONCILIABLE_STATUSES, threshold);

        int count = 0;
        for (ReservationProcess process : candidates) {
            if (process.getStatus() == ProcessStatus.HELD
                    || (process.getStatus().isFinal() && process.getStatus() != ProcessStatus.CONFIRMED)) {
                continue;
            }
            boolean confirmedLocally = process.getStatus() == ProcessStatus.CONFIRMED;
            try {
                Optional<CatedraAppointmentDTO> remote = catedraClient.findAppointment(
                        process.getProcessId(),
                        process.getExternalPatientId()
                );
                if (confirmedLocally) {
                    applyRemoteToConfirmed(process, remote.orElse(null));
                } else if (remote.isEmpty()) {
                    expireOrInvalidate(process);
                } else {
                    applyRemote(process, remote.get());
                }
                process.setUpdatedAt(Instant.now());
                processRepository.save(process);
                meterRegistry.counter("turnos.recovery.processes", "outcome", "reconciled").increment();
                count++;
            } catch (ApiException exception) {
                meterRegistry.counter("turnos.recovery.processes", "outcome", "retry",
                        "code", exception.getCode()).increment();
                log.warn("No se pudo reconciliar {}: {}", process.getProcessId(), exception.getCode());
            }
        }
        return count;
    }

    private void expireOrInvalidate(ReservationProcess process) {
        if (process.getExpiresAt() != null && process.getExpiresAt().isBefore(Instant.now())) {
            process.setStatus(ProcessStatus.EXPIRED);
            process.setReason("HOLD_EXPIRED");
        } else {
            process.setStatus(ProcessStatus.INVALID);
            process.setReason("REMOTE_NOT_FOUND");
        }
    }

    private void applyRemoteToConfirmed(ReservationProcess process, CatedraAppointmentDTO remote) {
        if (remote == null) {
            return;
        }
        if (remote.reservationId() != null) {
            process.setReservationId(remote.reservationId());
        }
        if ("CANCELLED".equalsIgnoreCase(remote.status())) {
            process.setStatus(ProcessStatus.CANCELLED);
            process.setCancelledAt(remote.cancelledAt());
            process.setReason(remote.cancellationReason());
            return;
        }
        if ("CONFIRMED".equalsIgnoreCase(remote.status())) {
            process.setReason(null);
        }
    }

    private void applyRemote(ReservationProcess process, CatedraAppointmentDTO remote) {
        ProcessStatus remoteStatus = mapRemoteStatus(remote.status());
        if (remoteStatus == null) {
            process.setStatus(ProcessStatus.INVALID);
            process.setReason("UNKNOWN_REMOTE_STATUS");
            return;
        }

        process.setStatus(remoteStatus);
        if (remote.reservationId() != null) {
            process.setReservationId(remote.reservationId());
        }
        if (remoteStatus == ProcessStatus.CANCELLED) {
            process.setCancelledAt(remote.cancelledAt());
            process.setReason(remote.cancellationReason());
        }
        if (remoteStatus == ProcessStatus.CONFIRMED) {
            process.setReason(null);
        }
    }

    private ProcessStatus mapRemoteStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "PHONE_PENDING" -> ProcessStatus.WAITING_FOR_PHONE;
            case "CONFIRMED" -> ProcessStatus.CONFIRMED;
            case "CANCELLED" -> ProcessStatus.CANCELLED;
            case "FAILED" -> ProcessStatus.FAILED;
            default -> null;
        };
    }
}
