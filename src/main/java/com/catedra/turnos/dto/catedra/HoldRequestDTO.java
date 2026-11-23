package com.catedra.turnos.dto.catedra;

import java.time.LocalDate;
import java.time.LocalTime;

public record HoldRequestDTO(
        Long professionalId,
        LocalDate date,
        LocalTime startTime,
        String externalPatientId
) {
}
