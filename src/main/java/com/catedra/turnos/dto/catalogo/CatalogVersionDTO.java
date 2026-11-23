package com.catedra.turnos.dto.catalogo;

import java.time.Instant;

public record CatalogVersionDTO(
        Long localVersion,
        Instant lastSyncedAt
) {
}
