# servicio-turnos

Servicio de turnos de la cátedra. Cubre la base del servicio, autenticación JWT, integración HTTP con `Servicio-catalogo` y con cátedra para disponibilidad, alta y confirmación de holds.

## Alcance actual

- Registro y login de usuarios con BCrypt.
- JWT HS256 con roles `USER`, `ADMIN` y `SERVICE`.
- Endpoint `/api/me`.
- Cliente HTTP hacia `Servicio-catalogo` con JWT técnico de servicio.
- Cliente HTTP hacia cátedra con JWT técnico para ocupaciones, alta de holds y confirmación.
- Endpoint `/api/availability` que genera slots desde los horarios del catálogo y descarta los ocupados por cátedra.
- Prioridad de `CONFIRMED` sobre `HELD`; los holds vencidos no bloquean la disponibilidad.
- Alta y confirmación de holds con persistencia local de `reservation_process` y `appointment_hold`.
- `externalPatientId` derivado del login autenticado; el cliente no puede elegirlo.
- Migración Flyway de usuarios, autoridades, roles, procesos y holds.
- Health y métricas vía Actuator.

Queda para próximos PR: teléfono vía Kafka, listado/cancelación de reservas, eventos de confirmación, idempotencia y recuperación.

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
| `POST` | `/api/appointment-holds/{holdId}/confirm` | Confirma el hold y deja el proceso en `WAITING_FOR_PHONE`. |
| `GET` | `/actuator/health` | Health check. |
