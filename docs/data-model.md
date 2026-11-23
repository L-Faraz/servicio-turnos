# Modelo de datos de `servicio-turnos`

## Tablas

| Tabla | Migración | Responsabilidad |
| --- | --- | --- |
| `authority`, `app_user`, `user_authority` | `V1__init_turnos.sql` | Usuarios finales, hash de password, autoridades y auditoría. |
| `reservation_process` | `V2__init_reservas.sql`, `V4__reserva_cancelacion.sql` | Proceso de reserva, estado, profesional, horario, teléfono, expiración, cancelación y motivo. |
| `appointment_hold` | `V2__init_reservas.sql` | Hold remoto por `holdId`, con su proceso, professional, horario, estado y vencimiento. |
| `processed_event` | `V3__kafka_outbox.sql` | Deduplicación de eventos Kafka por `event_id`. |
| `outbox_event` | `V3__kafka_outbox.sql` | Eventos pendientes de publicación hacia la cátedra con intentos y estado. |

## Propiedad de los datos

- La identidad del usuario final se guarda en `app_user` y se propaga como `user_id` en `reservation_process` y `appointment_hold`.
- `external_patient_id` se deriva del `login` local y nunca se recibe del cliente.
- El `groupId` técnico de cátedra no identifica al usuario final y no se usa para filtrar reservas propias.
- Los datos descriptivos del profesional se guardan en el proceso para trazabilidad histórica, pero la vigencia se valida contra `Servicio-catalogo` antes de crear o confirmar holds.
- El catálogo no se replica en esta base; solo se consulta por HTTP autenticado.

## Estados

`HELD`, `WAITING_FOR_PHONE`, `CONFIRMED`, `CANCELLED`, `EXPIRED` e `INVALID`. Los estados finales no retroceden ante eventos tardíos. La cancelación permite `CONFIRMED → CANCELLED` cuando la cátedra confirmó una cancelación remota.
