package com.catedra.turnos.dto;

import java.time.Instant;

public record ConfirmedHoldResponse(
        String processId,
        String status,
        Instant expiresAt
) {
}
