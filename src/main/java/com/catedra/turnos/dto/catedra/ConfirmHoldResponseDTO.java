package com.catedra.turnos.dto.catedra;

import java.time.Instant;

public record ConfirmHoldResponseDTO(
        String reservationProcessId,
        String status,
        Instant expiresAt
) {
}
