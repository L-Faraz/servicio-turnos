package com.catedra.turnos.dto.catedra;

public record ConfirmHoldRequestDTO(
        String externalPatientId,
        String patientFirstName,
        String patientLastName
) {
}
