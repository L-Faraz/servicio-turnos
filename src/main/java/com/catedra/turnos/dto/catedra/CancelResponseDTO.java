package com.catedra.turnos.dto.catedra;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CancelResponseDTO(
        Long reservationId,
        String reservationProcessId,
        String status,
        Instant cancelledAt,
        @JsonAlias("cancellationReason") String reason
) {
}
