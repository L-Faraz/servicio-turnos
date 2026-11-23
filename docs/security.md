# Seguridad de `servicio-turnos`

## Identidades

| Identidad | Uso |
| --- | --- |
| Usuario final | Se registra y autentica en este servicio; recibe un JWT HS256 con roles `USER`, `ADMIN` o `SERVICE`. |
| Cuenta técnica de cátedra | Se usa solo en el servidor para REST, Kafka y la integración. Nunca se expone a KMP ni a usuarios finales. |
| Servicio catálogo | Se llama por HTTP con un JWT de servicio firmado con el mismo `APP_JWT_SECRET`. |

## Reglas

- `passwordHash` se genera con BCrypt; la password nunca se guarda ni se devuelve en texto plano.
- `/api/**` requiere JWT válido, salvo registro y login.
- La propiedad de reservas se resuelve con el `sub` del JWT del usuario final, nunca con el `groupId` técnico.
- CORS se configura con `APP_CORS_ALLOWED_ORIGINS` y expone `Authorization`, `X-Total-Count` y `Link`.
- `/actuator/health` es público; `/actuator/metrics` requiere `ADMIN`.

## Secretos

| Variable | Uso |
| --- | --- |
| `APP_JWT_SECRET` | Secreto HS256 de al menos 32 bytes. |
| `CATEDRA_LOGIN_USERNAME`, `CATEDRA_LOGIN_PASSWORD` | Credenciales de la cuenta técnica; se usan para obtener JWT y configuración vigente. |
| `CATEDRA_JWT_TOKEN` | JWT técnico alternativo cuando no se usan credenciales de login. |
| `DB_PASSWORD`, `CATEDRA_KAFKA_*` | Credenciales de PostgreSQL y Kafka. |

Todos los secretos se externalizan por variables de entorno. `.env` es local y no se versiona; los logs no incluyen tokens ni teléfonos.
