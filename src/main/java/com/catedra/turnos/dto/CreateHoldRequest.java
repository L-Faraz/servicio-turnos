package com.catedra.turnos.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateHoldRequest(
        @NotNull Long professionalId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime
) {
}
