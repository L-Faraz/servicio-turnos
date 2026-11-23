package com.catedra.turnos.dto.catedra;

import java.time.Instant;

public record HoldResponseDTO(
        String holdId,
        String reservationProcessId,
        String status,
        Instant expiresAt
) {
}
