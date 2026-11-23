# servicio-turnos

Servicio de turnos de la cátedra. Este PR cubre la base del servicio: usuarios, autenticación JWT, integración HTTP con `Servicio-catalogo` y consulta de disponibilidad a partir de horarios del catálogo.

## Alcance actual

- Registro y login de usuarios con BCrypt.
- JWT HS256 con roles `USER`, `ADMIN` y `SERVICE`.
- Endpoint `/api/me`.
- Cliente HTTP hacia `Servicio-catalogo` con JWT técnico de servicio.
- Endpoint `/api/availability` que genera slots desde los horarios publicados por el catálogo.
- Migración Flyway inicial de usuarios, autoridades y roles.
- Health y métricas vía Actuator.

Queda para próximos PR: integración con cátedra, holds, reservas, eventos Kafka, idempotencia y recuperación.

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
| `DB_URL` | JDBC de PostgreSQL. |
| `DB_USERNAME` / `DB_PASSWORD` | Credenciales de PostgreSQL. |

## Ejecución local con Compose

```bash
cp .env.example .env
docker compose up --build -d
curl http://localhost:8082/actuator/health
docker compose logs -f servicio-turnos
```

Para ejecutar contra el catálogo local, levantar primero `Servicio-catalogo` en `http://localhost:8081` y dejar `CATALOG_BASE_URL=http://host.docker.internal:8081`.

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

## Endpoints PR 1

| Método | Ruta | Descripción |
| --- | --- | --- |
| `POST` | `/api/register` | Registro de usuario. |
| `POST` | `/api/authenticate` | Login y devolución del JWT. |
| `GET` | `/api/me` | Usuario autenticado. |
| `GET` | `/api/availability?professionalId=15&from=2026-11-23&to=2026-11-30` | Slots desde el catálogo. |
| `GET` | `/actuator/health` | Health check. |
