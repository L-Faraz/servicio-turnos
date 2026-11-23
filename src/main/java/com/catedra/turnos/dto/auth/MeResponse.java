package com.catedra.turnos.dto.auth;

import java.util.List;

public record MeResponse(
        String login,
        List<String> roles
) {
}
