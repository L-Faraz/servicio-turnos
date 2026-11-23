# servicio-turnos

Servicio de turnos de la cátedra. Cubre autenticación JWT, integración HTTP con `Servicio-catalogo` y cátedra, confirmación REST + Kafka, envío de teléfono vía outbox e idempotencia de eventos.

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
- Eventos de confirmación, rechazo, expiración, proceso inválido y cancelación actualizan el proceso local.
- Migración Flyway de usuarios, autoridades, roles, procesos, holds, outbox y eventos procesados.
- Health y métricas vía Actuator.

Queda para próximo PR: listado/cancelación de reservas, expiración local y recuperación/observabilidad adicional.

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
| `CATEDRA_JWT_TOKEN` | JWT técnico de la cuenta de integración de cátedra; nunca se expone al cliente. |
| `CATEDRA_GROUP_ID` | Grupo de integración; por defecto `proyecto-l-faraz`. |
| `CATEDRA_KAFKA_BOOTSTRAP_SERVERS` | Brokers Kafka. En cátedra, `172.17.201.1:9094` en `PLAINTEXT`. |
| `CATEDRA_KAFKA_ENABLED` | Habilita consumidor y publicación; desactivar en tests. |
| `TURNOS_OUTBOX_ENABLED` | Habilita el publicador periódico del outbox. |
| `DB_URL` | JDBC de PostgreSQL. |
| `DB_USERNAME` / `DB_PASSWORD` | Credenciales de PostgreSQL. |

## Ejecución local con Compose

```bash
cp .env.example .env
docker compose up --build -d
curl http://localhost:8082/actuator/health
docker compose logs -f servicio-turnos
```

Para ejecutar contra el catálogo local, levantar primero `Servicio-catalogo` en `http://localhost:8081` y dejar `CATALOG_BASE_URL=http://host.docker.internal:8081`. Para consultar ocupaciones reales, completar `CATEDRA_API_URL` y `CATEDRA_JWT_TOKEN` con la cuenta técnica; sin esos valores `/api/availability` responde `503 INTEGRATION_NOT_CONFIGURED`.

Para integrar con Kafka de cátedra, dejar `CATEDRA_GROUP_ID=proyecto-l-faraz`, `CATEDRA_KAFKA_BOOTSTRAP_SERVERS=172.17.201.1:9094` y `CATEDRA_KAFKA_SECURITY_PROTOCOL=PLAINTEXT`. Los topics y el consumer group se derivan del `groupId` salvo override explícito. El topic de teléfono es `catedra.turnos.telefono.proyecto-l-faraz` y la message key de todos los eventos es el `reservationProcessId`.

## Ejecución local con Gradle

```bash
export APP_JWT_SECRET='replace-with-at-least-32-bytes'
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
| `GET` | `/actuator/health` | Health check. |
