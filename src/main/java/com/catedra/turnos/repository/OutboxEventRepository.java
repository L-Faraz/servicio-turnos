package com.catedra.turnos.repository;

import com.catedra.turnos.domain.OutboxEvent;
import com.catedra.turnos.domain.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    Optional<OutboxEvent> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    List<OutboxEvent> findAllByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
            Collection<OutboxStatus> statuses,
            Instant nextAttemptAt
    );
}
