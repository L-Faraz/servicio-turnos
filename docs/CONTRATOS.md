# Contratos

## Autenticación

Todas las rutas `/api/**` requieren `Authorization: Bearer <JWT>` salvo `/api/register` y `/api/authenticate`. El JWT technical de cátedra se usa únicamente en las llamadas servidor a servidor.

## Endpoints locales

| Método | Ruta | Descripción |
| --- | --- | --- |
| `POST` | `/api/register` | Registro de usuario. |
| `POST` | `/api/authenticate` | Login; devuelve JWT HS256. |
| `GET` | `/api/me` | Usuario autenticado. |
| `GET` | `/api/availability?professionalId=&from=&to=` | Slots libres del catálogo. |
| `POST` | `/api/appointment-holds` | Crea hold; body `{professionalId, date, startTime}`. |
| `POST` | `/api/appointment-holds/{holdId}/confirm` | Confirma el hold. |
| `POST` | `/api/reservations/{processId}/phone` | Envía teléfono; body `{phoneNumber}`. |
| `GET` | `/api/appointments` | Lista reservas del usuario autenticado. |
| `POST` | `/api/appointments/{processId}/cancel` | Cancela una reserva; body opcional `{reason}`. |

### `GET /api/appointments`

Filtros opcionales: `status`, `from`, `to`, `page`, `size`, `sort`. `status` admite `PHONE_PENDING`, `CONFIRMED`, `CANCELLED` y `FAILED`; `sort` admite `appointmentDate|startTime|professionalId|status,asc|desc` y, si se omite, cátedra aplica el orden contractual por fecha y hora descendentes. Un sort inválido devuelve `INVALID_SORT` (400). `externalPatientId` se deriva del login autenticado. El backend recorre el listado remoto, conserva sólo las reservas con proceso local del usuario y aplica `page`/`size` sobre ese conjunto. `X-Total-Count` y `Link` se calculan sobre las reservas propias; `Link` apunta a este endpoint local.

Cada item incluye los campos del contrato de cátedra más `localStatus`, `requestEventId` y `expiresAt`.

### `POST /api/appointments/{processId}/cancel`

`reason` es opcional y admite hasta 500 caracteres. Sólo se cancelan procesos locales `CONFIRMED`; `CANCELLED` es idempotente. En éxito devuelve `200` con `reservationId`, `reservationProcessId`, `status=CANCELLED`, `cancelledAt` y `reason`. Si la llamada remota falla por timeout o `RESERVATION_NOT_CANCELLABLE`, se consulta el estado remoto y se aplica la cancelación si cátedra ya la registró.

Errores propios: `PROCESS_NOT_FOUND` (404, también para procesos de otro usuario), `VALIDATION_ERROR` (400), `RESERVATION_NOT_CANCELLABLE` (409), `RESERVATION_OWNER_MISMATCH` (403 si lo devuelve cátedra), `RESERVATION_NOT_FOUND` (404 de cátedra), `CATEDRA_UNAVAILABLE` (503), `CATEDRA_PAGINATION_LIMIT` (502) y `CATEDRA_ERROR` (502).

## API externa de cátedra

- `GET /api/appointment-occupancies`: ocupaciones por profesional y rango.
- `POST /api/appointment-holds`: alta de hold.
- `POST /api/appointment-holds/{holdId}/confirm`: confirmación inicial.
- `GET /api/appointments`: listado con filtros y paginación Spring/JHipster.
- `POST /api/appointments/{reservationProcessId}/cancel`: cancelación.

Los errores funcionales se leen del campo `code` del `ProblemDetail`; nunca del `detail`.

## Kafka

| Topic | Dirección | Eventos |
| --- | --- | --- |
| `alumnos.turnos.acciones.{groupId}` | cátedra → alumno | `AdditionalInformationRequested`, `AppointmentConfirmed`, `AdditionalInformationRejected`, `AppointmentProcessExpired`, `AppointmentProcessInvalid`, `AppointmentCancelled` |
| `catedra.turnos.telefono.{groupId}` | alumno → cátedra | `AdditionalInformationSubmitted` |
| `alumnos.turnos.acciones.{groupId}.dlt` | alumno | eventos fallidos |

Todos los eventos usan `schemaVersion=1` y message key igual a `reservationProcessId`. `AdditionalInformationSubmitted` conserva el `requestEventId` recibido y envía `information.phoneNumber` normalizado. El `eventId` del teléfono cambia en cada rechazo porque incluye `phoneAttempt`.

## Mapa de estados

| Remoto | Local |
| --- | --- |
| `HELD` | `HELD` |
| `PHONE_PENDING` | `WAITING_FOR_PHONE` |
| `CONFIRMED` | `CONFIRMED` |
| `CANCELLED` | `CANCELLED` |
| `FAILED` | `FAILED` |
| `HOLD_EXPIRED` | `EXPIRED` |
| `REQUEST_EVENT_MISMATCH` | `INVALID` |
