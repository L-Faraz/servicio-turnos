package com.catedra.turnos.dto.catedra;

import java.util.List;

public record AppointmentPage(
        List<CatedraAppointmentDTO> content,
        Long totalCount,
        String link
) {
}
