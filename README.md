# servicio-turnos

Servicio de turnos de la cátedra. Cubre autenticación JWT, integración HTTP con `Servicio-catalogo` y cátedra, confirmación REST + Kafka, envío de teléfono vía outbox, idempotencia de eventos, listado, cancelación y recuperación.

## Alcance actual

- Registro y login de usuarios con BCrypt.
- JWT HS256 con roles `USER`, `ADMIN` y `SERVICE`.
- Endpoint `/api/me`.
- Cliente HTTP hacia `Servicio-catalogo` con JWT técnico de servicio.
- Cliente HTTP hacia cátedra con JWT técnico para ocupaciones, alta de holds y confirmación.
- Endpoint `/api/availability` que genera slots desde los horarios del catálogo y descarta los ocupados por cátedra.
- Prioridad de `CONFIRMED` sobre `HELD`; los holds vencidos no bloquean la disponibilidad.
- Alta y confirmación de holds con persistencia local de `reservation_process` y `appointment_hold`.
- Revalidación del profesional en catálogo antes de confirmar un hold.
- `externalPatientId` derivado del login autenticado; el cliente no puede elegirlo.
- Consumidor Kafka de `alumnos.turnos.acciones.{groupId}` con DLT, ack manual e idempotencia por `eventId`.
- Envío de `AdditionalInformationSubmitted` mediante outbox transaccional, reintentos y métricas.
- Rechazo de teléfono con nuevo `eventId` determinista por intento sin reutilizar el `requestEventId`.
- Eventos de confirmación, rechazo, expiración, proceso inválido y cancelación actualizan el proceso local; los eventos tardíos no reabren estados finales.
- Listado paginado de reservas propias con filtro local de propiedad, cancelación idempotente de reservas confirmadas y propagación de códigos funcionales.
- Expiración local y reconciliación REST de procesos inactivos.
- Métricas de listado, cancelación y recuperación además de health vía Actuator.
- Migración Flyway de usuarios, autoridades, roles, procesos, holds, outbox, eventos procesados y cancelación.

Documentación adicional: [arquitectura](docs/ARQUITECTURA.md), [modelo de datos](docs/data-model.md), [contratos](docs/CONTRATOS.md), [idempotencia y recuperación](docs/idempotency-and-recovery.md), [seguridad](docs/security.md), [pruebas](docs/tests.md) y [evidencias](docs/EVIDENCIAS.md).

## Requisitos

- Java 25.
- Docker y Docker Compose, o PostgreSQL local en el puerto `5433`.

## Variables principales

Copiar `.env.example` a `.env` y completar al menos:

| Variable | Descripción |
| --- | --- |
| `APP_JWT_SECRET` | Secreto HS256 de al menos 32 bytes. |
| `APP_JWT_ISSUER` | Issuer de los JWT emitidos por turnos. |
| `CATALOG_BASE_URL` | URL base de `Servicio-catalogo`. |
| `CATEDRA_API_URL` | URL base del servicio central de cátedra. |
| `CATEDRA_LOGIN_USERNAME` / `CATEDRA_LOGIN_PASSWORD` | Credenciales de la cuenta técnica; si se completan, el servicio hace login y recupera la configuración vigente al arrancar. |
| `CATEDRA_JWT_TOKEN` | JWT técnico de la cuenta de integración de cátedra; nunca se expone al cliente. |
| `CATEDRA_GROUP_ID` | `groupId` entregado con la cuenta técnica; sin valor por defecto. |
| `CATEDRA_KAFKA_BOOTSTRAP_SERVERS` | Brokers Kafka entregados con la cuenta técnica; sin valor por defecto. |
| `CATEDRA_KAFKA_ENABLED` | Habilita consumidor y publicación; desactivar en tests. |
| `TURNOS_OUTBOX_ENABLED` | Habilita el publicador periódico del outbox. |
| `TURNOS_RECOVERY_ENABLED` | Habilita expiración local y reconciliación periódica. |
| `TURNOS_RECOVERY_STALE_AFTER_MS` | Antigüedad mínima para reconciliar un proceso. |
| `DB_URL` | JDBC de PostgreSQL. |
| `DB_USERNAME` / `DB_PASSWORD` | Credenciales de PostgreSQL. |

La cuenta técnica se crea una sola vez con `POST /api/student/register` desde Postman o una herramienta HTTP equivalente. Con `CATEDRA_API_URL`, `CATEDRA_LOGIN_USERNAME` y `CATEDRA_LOGIN_PASSWORD`, el servicio autentica esa cuenta con `rememberMe=true`, recupera `GET /api/student/integration` y aplica el JWT, `groupId`, brokers y topics devueltos antes de crear los beans Kafka. Si no se completan las credenciales, se pueden informar los valores individuales de configuración.

## Ejecución local con Compose

```bash
cp .env.example .env
docker compose up --build -d
curl http://localhost:8082/actuator/health
docker compose logs -f servicio-turnos
```

Para ejecutar contra el catálogo local, levantar primero `Servicio-catalogo` en `http://localhost:8081` y dejar `CATALOG_BASE_URL=http://host.docker.internal:8081`. Para consultar ocupaciones reales, completar el login técnico o `CATEDRA_API_URL` y `CATEDRA_JWT_TOKEN` con la cuenta técnica; sin esos valores `/api/availability` responde `503 INTEGRATION_NOT_CONFIGURED`.

Para integrar con Kafka de cátedra, completar `CATEDRA_GROUP_ID`, `CATEDRA_KAFKA_BOOTSTRAP_SERVERS` y `CATEDRA_KAFKA_SECURITY_PROTOCOL` con los valores entregados por la cátedra, o dejar que el login técnico los recupere. Los topics y el consumer group se derivan del `groupId` salvo override explícito. El topic de teléfono sigue el formato `catedra.turnos.telefono.{groupId}` y la message key de todos los eventos es el `reservationProcessId`.

## Ejecución local con Gradle

```bash
export APP_JWT_SECRET='replace-with-at-least-32-byte-secret-value'
export DB_URL='jdbc:postgresql://localhost:5433/turnos_db'
export DB_USERNAME='postgres'
export DB_PASSWORD='postgres'
./gradlew bootRun --no-daemon
```

## Build y tests

```bash
./gradlew clean compileJava
./gradlew compileTestJava
./gradlew test
./gradlew clean check build --no-daemon
```

Los tests incluyen H2 para el contexto web, Testcontainers con PostgreSQL para validar Flyway y mocks para servicios.

## Endpoints actuales

| Método | Ruta | Descripción |
| --- | --- | --- |
| `POST` | `/api/register` | Registro de usuario. |
| `POST` | `/api/authenticate` | Login y devolución del JWT. |
| `GET` | `/api/me` | Usuario autenticado. |
| `GET` | `/api/availability?professionalId=15&from=2026-11-23&to=2026-11-30` | Slots del catálogo menos ocupaciones `CONFIRMED`/`HELD` de cátedra. |
| `POST` | `/api/appointment-holds` | Crea un hold validando catálogo y disponibilidad. |
| `POST` | `/api/appointment-holds/{holdId}/confirm` | Revalida catálogo, confirma el hold y deja el proceso en `WAITING_FOR_PHONE`. |
| `POST` | `/api/reservations/{processId}/phone` | Publica `AdditionalInformationSubmitted` en el outbox Kafka. |
| `GET` | `/api/appointments` | Lista reservas propias con filtros y paginación. |
| `POST` | `/api/appointments/{processId}/cancel` | Cancela una reserva propia. |
| `GET` | `/actuator/health` | Health check. |
