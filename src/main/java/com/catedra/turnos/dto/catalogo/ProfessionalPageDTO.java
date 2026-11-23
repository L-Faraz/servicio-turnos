package com.catedra.turnos.dto.catalogo;

import java.util.List;

public record ProfessionalPageDTO(
        List<CatalogProfessionalDTO> content,
        long totalElements
) {
}
