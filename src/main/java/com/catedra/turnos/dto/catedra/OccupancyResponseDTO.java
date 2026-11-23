package com.catedra.turnos.dto.catedra;

import java.time.LocalDate;
import java.util.List;

public record OccupancyResponseDTO(
        Long professionalId,
        LocalDate from,
        LocalDate to,
        List<OccupancyDTO> occupancies
) {
}
