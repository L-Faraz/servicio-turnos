package com.catedra.turnos.service;

import com.catedra.turnos.config.TurnosProperties;
import com.catedra.turnos.domain.OutboxEvent;
import com.catedra.turnos.domain.enums.OutboxStatus;
import com.catedra.turnos.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxService service;

    @BeforeEach
    void setUp() {
        service = new OutboxService(
                repository,
                kafkaTemplate,
                new ObjectMapper(),
                new TurnosProperties(),
                new SimpleMeterRegistry()
        );
    }

    @Test
    void enqueuePersistsPendingEvent() {
        service.enqueue("event-1", "topic-1", "process-1", Map.of("phoneNumber", "+5492615551234"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).saveAndFlush(captor.capture());
        OutboxEvent event = captor.getValue();
        assertThat(event.getEventId()).isEqualTo("event-1");
        assertThat(event.getTopic()).isEqualTo("topic-1");
        assertThat(event.getMessageKey()).isEqualTo("process-1");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getPayload()).contains("+5492615551234");
    }

    @Test
    void publishPendingMarksEventAsPublished() {
        OutboxEvent event = pendingEvent();
        when(repository.findAllByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(any(), any()))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send("topic-1", "process-1", "{}"))
                .thenReturn(CompletableFuture.completedFuture(null));

        int processed = service.publishPending();

        assertThat(processed).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void publishPendingSchedulesRetryOnKafkaFailure() {
        OutboxEvent event = pendingEvent();
        when(repository.findAllByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(any(), any()))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send("topic-1", "process-1", "{}"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        service.publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("broker down");
        assertThat(event.getNextAttemptAt()).isAfter(Instant.now());
    }

    private OutboxEvent pendingEvent() {
        OutboxEvent event = new OutboxEvent();
        event.setEventId("event-1");
        event.setTopic("topic-1");
        event.setMessageKey("process-1");
        event.setPayload("{}");
        event.setStatus(OutboxStatus.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(Instant.now().minusSeconds(1));
        event.setCreatedAt(Instant.now());
        return event;
    }
}
