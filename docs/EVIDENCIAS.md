# Evidencias

## Tests automáticos

Comando reproducible:

```bash
./gradlew clean check build --no-daemon
```

Cubre:

- `ReservationServiceTest`: alta y confirmación de holds, revalidación de catálogo, envío de teléfono, idempotencia de teléfono, listado con propiedad por usuario, paginación local, cancelación por estado y errores funcionales.
- `ReservationEventServiceTest`: solicitud de teléfono, rechazo con nuevo intento, confirmación, expiración, proceso inválido, cancelación y eventos tardíos sobre estados finales.
- `RecoveryServiceTest`: expiración local, reconciliación remota, cancelación de un `CONFIRMED` y manejo de errores de cátedra.
- `TurnosKafkaListenerTest`: validación de contrato, idempotencia y ack.
- `OutboxServiceTest`: persistencia, publicación y reintento acotado.
- `CatedraClientTest`: ocupaciones, holds, confirmación, listado, búsqueda paginada por `processId`, cancelación y códigos funcionales.
- `PostgresFlywayIntegrationTest`: esquema con PostgreSQL y migraciones `V1` a `V4`.

## Flujo manual

```bash
export TOKEN=$(curl -s -X POST http://localhost:8082/api/authenticate \
  -H 'Content-Type: application/json' \
  -d '{"login":"juan.perez","password":"..."}' | jq -r .token)

curl -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8082/api/availability?professionalId=15&from=2026-11-23&to=2026-11-30'

curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"professionalId":15,"date":"2026-11-23","startTime":"09:00"}' \
  http://localhost:8082/api/appointment-holds

curl -X POST -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/api/appointment-holds/hold-1/confirm

curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"+5492615551234"}' \
  http://localhost:8082/api/reservations/process-1/phone

curl -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8082/api/appointments?status=CONFIRMED&page=0&size=20'

curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"reason":"Cambio solicitado por el usuario"}' \
  http://localhost:8082/api/appointments/process-1/cancel
```

## Casos verificables

- Confirmación REST + Kafka: el hold pasa a `WAITING_FOR_PHONE` y `AppointmentConfirmed` deja el proceso en `CONFIRMED`.
- Vencimiento sin teléfono: `RecoveryService` pasa el proceso a `EXPIRED` con `reason=HOLD_EXPIRED` y bloquea nuevas acciones.
- Listado propio: se deriva `externalPatientId` del JWT, se recorre el listado remoto y se descarta cualquier reserva sin proceso local del usuario; la paginación y `X-Total-Count` son locales.
- Cancelación propia: sólo un proceso local `CONFIRMED` puede cancelarse; un `CANCELLED` es idempotente y un proceso ajeno devuelve `PROCESS_NOT_FOUND`.
- Duplicado Kafka: el segundo `eventId` se descarta vía `processed_event` y se incrementa `turnos.kafka.events{outcome=duplicate}`.
- Rechazo de teléfono: `phoneAttempt` aumenta y el siguiente `AdditionalInformationSubmitted` usa un `eventId` nuevo.
- Cancelación idempotente: una segunda cancelación local devuelve el estado guardado sin nueva llamada remota.
- Cancelación remota confirmada sin commit local: `findAppointment` recorre todas las páginas y `RecoveryService` reconcilia un `CONFIRMED` con el `CANCELLED` de cátedra sin reabrir ni degradar estados finales.
- Cancelación con confirmación Kafka perdida: si el proceso local aún está `WAITING_FOR_PHONE`/`PHONE_SUBMITTED`, se consulta cátedra, se converge a `CONFIRMED` y se cancela.
