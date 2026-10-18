# ms-andesstay-reservations

Microservicio de reservas de AndesStay (puerto **8081**). Spring Boot 4.1.1 · Java 17 · JPA + H2 · Resource Server JWT (Microsoft Entra ID) · **productor RabbitMQ** (EP3).

## Ejecutar

```powershell
# 1. RabbitMQ (opcional: si esta caido, las reservas se guardan igual y solo se loguea un warning)
docker compose -f ..\infra\mq\compose.yml up -d

# 2. Microservicio
.\mvnw.cmd spring-boot:run
# Con el cluster de 2 nodos:
$env:RABBITMQ_ADDRESSES="localhost:5672,localhost:5673"; .\mvnw.cmd spring-boot:run
```

| Variable | Default | Uso |
|---|---|---|
| `RABBITMQ_ADDRESSES` | `localhost:5672` | nodos del broker (separados por coma) |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `guest` / `guest` | credenciales |
| `RABBITMQ_VHOST` | `/` | virtual host |
| `ANDESSTAY_DLQ_RETENTION` | `7d` | `x-message-ttl` de las DLQ |

## Endpoints

Todos requieren `Authorization: Bearer <JWT de Entra ID>` (normalmente se llaman a traves del BFF en `:8080`).

| Metodo | Ruta | Descripcion | Codigos |
|---|---|---|---|
| GET | `/api/reservations` | Lista reservas | 200 |
| GET | `/api/reservations?status=CONFIRMADA` | Filtra por estado (EP3) | 200, 400 |
| GET | `/api/reservations/{id}` | Detalle | 200, 404 |
| POST | `/api/reservations` | Crea (siempre parte en `CREADA`) | 200 |
| PUT | `/api/reservations/{id}` | Actualiza datos (no el estado) | 200, 404 |
| DELETE | `/api/reservations/{id}` | Elimina | 204, 404 |
| PUT | `/api/reservations/{id}/status` | Cambia estado (EP3), body `{"status":"CONFIRMADA"}` | 200, 400, 404, 409 |

```bash
curl -X PUT http://localhost:8081/api/reservations/1/status \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"status":"CONFIRMADA"}'
```

## Maquina de estados

```
CREADA --> CONFIRMADA --> CHECKIN_PENDIENTE --> EN_ESTADIA --> CHECKOUT
  |            |                  |
  +------------+------------------+--> CANCELADA
```

Una transicion que no esta en el diagrama devuelve **409 Conflict** con el detalle de las permitidas. Regla del caso: **no hay check-in sin confirmar** (`CREADA -> CHECKIN_PENDIENTE` = 409).

## Mensajeria (EP3)

Cada cambio de estado publica comandos a RabbitMQ a traves de la interfaz `service/ReservationNotificationPublisher` (el dominio no conoce RabbitMQ; la implementacion esta en `messaging/RabbitReservationNotificationPublisher`).

| Estado | Comandos (`type`) | Exchange -> routing key |
|---|---|---|
| CREADA | `EMAIL_RESERVA_CREADA` | `cmd.direct` -> `email.send` |
| CONFIRMADA | `EMAIL_RESERVA_CONFIRMADA`, `VOUCHER_GENERAR`, `HOUSEKEEPING_PREPARACION` | `cmd.direct` -> `email.send` / `voucher.gen`; `cmd.topic` -> `housekeeping.ticket.preparacion` |
| CHECKIN_PENDIENTE | `EMAIL_RECORDATORIO_CHECKIN` | `cmd.direct` -> `email.send` |
| EN_ESTADIA | (ninguno) | |
| CHECKOUT | `EMAIL_CHECKOUT`, `HOUSEKEEPING_LIMPIEZA`, `BOLETA_GENERAR` | `cmd.direct` -> `email.send` / `voucher.gen`; `cmd.topic` -> `housekeeping.ticket.limpieza` |
| CANCELADA | `EMAIL_RESERVA_CANCELADA` | `cmd.direct` -> `email.send` |

- Envelope comun (`messaging/EventEnvelope`): `type, eventId, timestamp, traceId, correlationId, source, payload`.
- Mensajes persistentes, `content-type: application/json`, `messageId = eventId`, header `x-trace-id`.
- **Publisher confirms** (`correlated`) y **publisher returns** con `mandatory=true`: los callbacks loguean `[CONFIRM]` y `[RETURN]`.
- Si el broker esta caido: `[PUBLISH-FAIL]` en log (warn) y la reserva queda guardada igual.
- Topologia declarada en `messaging/RabbitTopologyConfig`; todos los nombres vienen de `andesstay.messaging.*` en `application.yaml`.

## Tests

```powershell
.\mvnw.cmd clean package
```

- `ReservationStatusTest`: transiciones validas e invalidas.
- `ReservationServiceTest`: CREADA forzado, 404, 409 sin publicar.
- `RabbitReservationNotificationPublisherTest`: exchanges/keys por estado y tolerancia a broker caido.
