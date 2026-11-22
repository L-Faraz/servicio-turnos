package com.catedra.turnos.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record AvailableSlotDTO(
        Long professionalId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        boolean available,
        String reason
) {
}
