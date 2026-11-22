package com.catedra.turnos.dto.auth;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresIn
) {
}
