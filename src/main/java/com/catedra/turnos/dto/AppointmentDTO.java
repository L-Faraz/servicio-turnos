package com.catedra.turnos.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentDTO(
        Long reservationId,
        String reservationProcessId,
        String externalPatientId,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        Long professionalId,
        String professionalFirstName,
        String professionalLastName,
        String patientFirstName,
        String patientLastName,
        String patientPhone,
        String status,
        Instant createdAt,
        Instant confirmedAt,
        Instant cancelledAt,
        String cancellationReason,
        String localStatus,
        String requestEventId,
        Instant expiresAt
) {
}
