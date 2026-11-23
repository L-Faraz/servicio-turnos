package com.catedra.turnos.dto.catalogo;

public record CategoryDTO(
        Long id,
        String name,
        String description,
        boolean enabled
) {
}
