package com.catedra.turnos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TurnosEventDTO(
        String eventId,
        String eventType,
        String groupId,
        String reservationProcessId,
        String holdId,
        String requestEventId,
        String requiredField,
        String message,
        String reason,
        String status,
        Long reservationId,
        Long professionalId,
        LocalDate date,
        LocalTime startTime,
        Instant expiresAt,
        Instant occurredAt,
        Map<String, String> information,
        Integer schemaVersion
) {
}
