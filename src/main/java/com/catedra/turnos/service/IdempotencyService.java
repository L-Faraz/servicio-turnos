package com.catedra.turnos.service;

import com.catedra.turnos.domain.ProcessedEvent;
import com.catedra.turnos.repository.ProcessedEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class IdempotencyService {

    private final ProcessedEventRepository repository;

    public IdempotencyService(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    public boolean isProcessed(String eventId) {
        return repository.existsById(eventId);
    }

    @Transactional
    public void markProcessed(String eventId, String eventType) {
        try {
            repository.saveAndFlush(new ProcessedEvent(eventId, eventType, Instant.now()));
        } catch (DataIntegrityViolationException exception) {
            return;
        }
    }
}
