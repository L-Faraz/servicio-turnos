package com.catedra.turnos.dto.catalogo;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record WeeklyScheduleDTO(
        Long id,
        Long professionalId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Integer slotDurationMinutes,
        boolean enabled
) {
}
