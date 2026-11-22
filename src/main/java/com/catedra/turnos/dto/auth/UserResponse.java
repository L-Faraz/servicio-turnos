package com.catedra.turnos.dto.auth;

import java.util.Set;

public record UserResponse(
        Long id,
        String login,
        String email,
        String firstName,
        String lastName,
        String imageUrl,
        String langKey,
        Set<String> roles
) {
}
