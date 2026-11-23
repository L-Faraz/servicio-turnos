package com.catedra.turnos.dto.catedra;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record OccupancyDTO(
        LocalDate date,
        LocalTime startTime,
        String status,
        Instant expiresAt
) {
}
