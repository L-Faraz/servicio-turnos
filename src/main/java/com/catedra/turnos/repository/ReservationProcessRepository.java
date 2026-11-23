package com.catedra.turnos.repository;

import com.catedra.turnos.domain.ReservationProcess;
import com.catedra.turnos.domain.enums.ProcessStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationProcessRepository extends JpaRepository<ReservationProcess, String> {

    Optional<ReservationProcess> findByProcessIdAndUserId(String processId, Long userId);

    Optional<ReservationProcess> findByHoldIdAndUserId(String holdId, Long userId);

    Optional<ReservationProcess> findByHoldIdAndStatus(String holdId, ProcessStatus status);

    List<ReservationProcess> findAllByUserId(Long userId);

    List<ReservationProcess> findAllByUserIdAndStatusIn(Long userId, Collection<ProcessStatus> statuses);

    List<ReservationProcess> findAllByStatusInAndExpiresAtBefore(
            Collection<ProcessStatus> statuses,
            Instant expiresAt
    );

    List<ReservationProcess> findAllByStatusInAndUpdatedAtBefore(
            Collection<ProcessStatus> statuses,
            Instant updatedAt
    );

    List<ReservationProcess> findByUserIdAndProfessionalIdAndAppointmentDateAndStartTimeAndStatusIn(
            Long userId,
            Long professionalId,
            LocalDate appointmentDate,
            LocalTime startTime,
            Collection<ProcessStatus> statuses
    );
}
