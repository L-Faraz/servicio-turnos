package com.catedra.turnos.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitPhoneRequest(
        @NotBlank String phoneNumber
) {
}
