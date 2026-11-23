package com.catedra.turnos.dto.catalogo;

import java.util.List;

public record CatalogProfessionalDTO(
        Long id,
        String firstName,
        String lastName,
        Long categoryId,
        String categoryName,
        boolean enabled,
        List<WeeklyScheduleDTO> schedules
) {
}
