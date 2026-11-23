package com.catedra.turnos.dto;

import java.util.List;

public record AppointmentPageDTO(
        List<AppointmentDTO> content,
        Long totalCount,
        String link
) {
}
