package com.catedra.turnos.dto;

import java.time.Instant;
import java.util.Map;

public record AdditionalInformationSubmittedEvent(
        String eventId,
        String eventType,
        String groupId,
        String reservationProcessId,
        String holdId,
        String requestEventId,
        Map<String, String> information,
        Instant occurredAt,
        int schemaVersion
) {
}
