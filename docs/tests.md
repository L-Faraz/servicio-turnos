# Pruebas de `servicio-turnos`

## Ejecución

```bash
./gradlew clean check build --no-daemon
```

## Cobertura principal

| Área | Pruebas |
| --- | --- |
| Registro y autenticación | `AuthServiceTest`, `AuthControllerIntegrationTest`. |
| Migraciones y esquema | `PostgresFlywayIntegrationTest` con PostgreSQL/Testcontainers. |
| Validación y errores | `GlobalExceptionHandlerTest`, `AppointmentControllerTest`. |
| Disponibilidad y holds | `AvailabilityServiceTest`, `ReservationServiceTest`. |
| REST de cátedra | `CatedraClientTest`, incluye paginación, cancelación y códigos funcionales. |
| Kafka y outbox | `TurnosKafkaListenerTest`, `ReservationEventServiceTest`, `OutboxServiceTest`. |
| Recuperación | `RecoveryServiceTest`. |
| Configuración técnica | `CatedraIntegrationEnvironmentPostProcessorTest`, `CatedraPropertiesTest`. |

Los tests usan H2 o Testcontainers y no requieren el entorno real de cátedra. La configuración técnica se prueba con un servidor HTTP local.
