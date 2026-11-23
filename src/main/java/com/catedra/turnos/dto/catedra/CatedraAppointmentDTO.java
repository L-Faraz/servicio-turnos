package com.catedra.turnos.dto.catedra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatedraAppointmentDTO(
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
        String cancellationReason
) {
}
