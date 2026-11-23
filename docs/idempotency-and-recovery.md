# Idempotencia y recuperación de `servicio-turnos`

## Eventos Kafka entrantes

- La message key debe coincidir con `reservationProcessId` y el `groupId` del evento con la integración vigente.
- `TurnosKafkaListener` valida `eventId`, `eventType`, `schemaVersion`, `occurredAt` y los campos obligatorios por tipo.
- El evento se marca en `processed_event` y se aplica dentro de la misma transacción; un reintento duplicado se reconoce por `event_id` y se hace `ack` sin repetir efectos.
- Los eventos inválidos se contabilizan y se envían al DLT `<actions-topic>.dlt` tras reintentos acotados.

## Outbox saliente

- `submitPhone` registra el evento en `outbox_event` en la misma transacción que actualiza el proceso.
- `OutboxPublisher` reintenta con backoff acotado, registra `attempts` y deja eventos `FAILED` para diagnóstico.
- El publicador solo se registra cuando `turnos.outbox.enabled` y `catedra.kafka-enabled` están habilitados.

## Reconciliación REST

- `RecoveryService` expira procesos con `expiresAt` vencido y reconcilia procesos inactivos más antiguos que `turnos.recovery.stale-after-ms`.
- La búsqueda remota recorre todas las páginas usando `X-Total-Count` y `Link` antes de declarar `REMOTE_NOT_FOUND`.
- La cancelación reconcilia estados locales obsoletos y errores remotos permitidos antes de degradar a estado final local.
- Los eventos tardíos no reabren estados finales; `AppointmentCancelled` es la única transición tardía permitida desde `CONFIRMED`.

## Métricas

`turnos.kafka.events`, `turnos.outbox.events`, `turnos.recovery.processes`, `turnos.appointments.list` y `turnos.appointments.cancel`.
