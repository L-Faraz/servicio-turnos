package com.catedra.turnos.dto;

import jakarta.validation.constraints.Size;

public record CancelRequestDTO(
        @Size(max = 500) String reason
) {
}
