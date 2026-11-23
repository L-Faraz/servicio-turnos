package com.catedra.turnos.dto;

import java.time.Instant;

public record CreatedHoldResponse(
        String processId,
        String holdId,
        String status,
        Instant expiresAt
) {
}
