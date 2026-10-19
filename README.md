# ms-andesstay-reservations

Microservicio de reservas de AndesStay (puerto **8081**). Spring Boot 4.1.1 · Java 17 · JPA + Oracle Autonomous Database (H2 solo en tests) · Resource Server JWT (Microsoft Entra ID) · **productor RabbitMQ** (EP3).

## Ejecutar

```powershell
# 1. RabbitMQ (opcional: si esta caido, las reservas se guardan igual y solo se loguea un warning)
docker compose -f ..\infra\mq\compose.yml up -d

# 2. Microservicio (requiere la BD Oracle en OCI, ver seccion siguiente)
$env:DB_PASSWORD="<password de ANDESSTAY>"
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
| `DB_URL` | `jdbc:oracle:thin:@andesstaydb_tp?TNS_ADMIN=C:/oracle/wallet_andesstay` | alias TNS + carpeta del wallet |
| `DB_USERNAME` | `ANDESSTAY` | usuario de la BD |
| `DB_PASSWORD` | (sin default, obligatoria) | password de la BD |

## Base de datos Oracle en OCI

La persistencia usa **Oracle Autonomous Database (Always Free)** con conexion mTLS por wallet. El driver es `ojdbc11-production` (incluye `oraclepki` para leer el wallet). Hibernate detecta el dialecto y crea/actualiza la tabla `RESERVATIONS` (`ddl-auto: update`). Pool Hikari de 5 conexiones (el tier Always Free limita las sesiones).

### 1. Crear la BD

1. Consola OCI > **Oracle Database > Autonomous Database > Create Autonomous Database**.
2. Nombre de BD: `andesstaydb`, tipo de carga **Transaction Processing**, marcar **Always Free**.
3. Definir la password de `ADMIN` y en *Network access* dejar **Secure access from everywhere** con **mTLS requerido**.
4. Cuando quede *Available*, entrar a **Database Actions > SQL** como `ADMIN` y crear el usuario de la app:

```sql
CREATE USER ANDESSTAY IDENTIFIED BY "<password segura>";
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO ANDESSTAY;
ALTER USER ANDESSTAY QUOTA UNLIMITED ON DATA;
```

### 2. Descargar el wallet

1. En la BD: **Database connection > Download wallet** (tipo *Instance wallet*), definir una password para el wallet y descargar `Wallet_andesstaydb.zip`.
2. Descomprimir **fuera del repo**, por ejemplo en `C:\oracle\wallet_andesstay` (debe contener `tnsnames.ora`, `sqlnet.ora`, `cwallet.sso`, `ewallet.p12`...).
3. El alias `andesstaydb_tp` sale de `tnsnames.ora` (tambien existen `_high`, `_medium`, `_low`, `_tpurgent`).

El wallet, los `.zip` y el `.env` estan en `.gitignore`: **nunca se versionan**.

### 3. Variables de entorno

Ver `.env.example`. `DB_URL` combina el alias TNS con la carpeta del wallet (`TNS_ADMIN`); usar `/` en la ruta, tambien en Windows.

```powershell
$env:DB_URL="jdbc:oracle:thin:@andesstaydb_tp?TNS_ADMIN=C:/oracle/wallet_andesstay"
$env:DB_USERNAME="ANDESSTAY"
$env:DB_PASSWORD="<password de ANDESSTAY>"
.\mvnw.cmd spring-boot:run
```

### 4. Copiar el wallet a la EC2

```powershell
# Desde Windows (el destino debe existir: ssh ... "mkdir -p ~/wallet_andesstay")
scp -i .\andesstay.pem C:\oracle\wallet_andesstay\* ec2-user@<IP_EC2>:~/wallet_andesstay/
```

```bash
# En la EC2
chmod 700 ~/wallet_andesstay && chmod 600 ~/wallet_andesstay/*
export DB_URL="jdbc:oracle:thin:@andesstaydb_tp?TNS_ADMIN=/home/ec2-user/wallet_andesstay"
export DB_USERNAME=ANDESSTAY
export DB_PASSWORD='<password de ANDESSTAY>'
java -jar ms-andesstay-reservations-0.0.1-SNAPSHOT.jar
```

Si la app corre en Docker, montar el wallet como volumen de solo lectura (`-v /home/ec2-user/wallet_andesstay:/wallet:ro`) y usar `TNS_ADMIN=/wallet` en `DB_URL`.

### 5. Verificar

Crear una reserva por la API y en **Database Actions > SQL** como `ANDESSTAY`: `SELECT * FROM RESERVATIONS;`

## Endpoints

Todos requieren `Authorization: Bearer <JWT de Entra ID>` (normalmente se llaman a traves del BFF en `:8080`).

**Principal del token** (`security/JwtAuthConverter`): primer claim no vacio entre `preferred_username`, `upn`, `unique_name`, `email` y `sub` (los tokens v1 de Entra traen `upn`/`unique_name`). El claim `roles` se mapea a `ROLE_*`.

**Visibilidad por rol** (`security/ReservationAccess`):
- `Admin`, `Operador_Dominio` y `Auditor` ven todas las reservas.
- `Cliente_Dominio` solo ve y actualiza las suyas (`huespedEmail` = su principal, sin distinguir mayusculas). Una reserva ajena responde **404**.
- Los `@PreAuthorize` se aplican (`@EnableMethodSecurity`) con la misma matriz que el BFF (si no, **403**):

| Endpoint | Admin | Operador_Dominio | Cliente_Dominio | Auditor |
|---|---|---|---|---|
| GET (listar, `?status=`, `/{id}`) | si | si | si (solo las suyas) | si |
| POST / PUT `/{id}` | si | si | si (solo las suyas) | no |
| PUT `/{id}/status` | si | si | no | no |
| DELETE `/{id}` | si | no | no | no |

| Metodo | Ruta | Descripcion | Codigos |
|---|---|---|---|
| GET | `/api/reservations` | Lista reservas | 200 |
| GET | `/api/reservations?status=CONFIRMADA` | Filtra por estado (EP3) | 200, 400 |
| GET | `/api/reservations/{id}` | Detalle | 200, 404 |
| POST | `/api/reservations` | Crea (siempre parte en `CREADA`) | 200 |
| PUT | `/api/reservations/{id}` | Actualiza datos (no el estado) | 200, 404 |
| DELETE | `/api/reservations/{id}` | Elimina | 204, 404 |
| PUT | `/api/reservations/{id}/status` | Cambia estado (EP3), body `{"status":"CONFIRMADA"}` | 200, 400, 404, 409 |

### Datos del huesped (POST)

Body (`dto/ReservationCreateRequest`): `alojamientoNombre, fechaInicio, fechaFin, precioTotal, huespedNombre, huespedEmail`. `id`, `estado` y `creadoPor` nunca se toman del body.

| Quien crea | `huespedNombre` | `huespedEmail` | `creadoPor` |
|---|---|---|---|
| `Cliente_Dominio` | claim `name` del token (el body se ignora) | principal del token | principal del token |
| `Admin` / `Operador_Dominio` | body, `@NotBlank` | body, `@NotBlank` + `@Email` | principal del token |

Si faltan o son invalidos responde **400**, por ejemplo `{"status":400,"message":"Datos del huesped invalidos: huespedEmail: es obligatorio"}`. Las respuestas incluyen `huespedNombre`, `huespedEmail` y `creadoPor`; `clienteUsername` se mantiene solo por compatibilidad (= `creadoPor`) y no debe usarse para mostrar.

Las columnas `HUESPED_NOMBRE`, `HUESPED_EMAIL` y `CREADO_POR` las agrega `ddl-auto: update` como nullable (Oracle no permite agregar una columna `NOT NULL` a una tabla con filas); la obligatoriedad se valida al crear.

```bash
curl -X POST http://localhost:8081/api/reservations \
  -H "Authorization: Bearer $TOKEN_ADMIN" -H "Content-Type: application/json" \
  -d '{"alojamientoNombre":"Cabana Lago","fechaInicio":"2026-12-01","fechaFin":"2026-12-05","precioTotal":250000,"huespedNombre":"Luis Soto","huespedEmail":"luis@correo.cl"}'

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
- Payload (`messaging/ReservationPayload`): `reservationId, huespedNombre, huespedEmail, clienteUsername, alojamientoNombre, fechaInicio, fechaFin, precioTotal, estado, estadoAnterior`. El destinatario de los correos es `huespedEmail` y el titular del voucher/boleta es `huespedNombre`; `clienteUsername` sigue viajando solo por compatibilidad con consumidores antiguos.
- Mensajes persistentes, `content-type: application/json`, `messageId = eventId`, header `x-trace-id`.
- **Publisher confirms** (`correlated`) y **publisher returns** con `mandatory=true`: los callbacks loguean `[CONFIRM]` y `[RETURN]`.
- Si el broker esta caido: `[PUBLISH-FAIL]` en log (warn) y la reserva queda guardada igual.
- Topologia declarada en `messaging/RabbitTopologyConfig`; todos los nombres vienen de `andesstay.messaging.*` en `application.yaml`.

## Tests

```powershell
.\mvnw.cmd clean package
```

Los tests no necesitan OCI: `MsAndesstayReservationsApplicationTests` activa el perfil `test` (`src/test/resources/application-test.yaml`, H2 en memoria).

- `ReservationStatusTest`: transiciones validas e invalidas.
- `ReservationServiceTest`: CREADA forzado con huesped y `creadoPor`, 404, 409 sin publicar, filtro por `huespedEmail` en listar/buscar/actualizar.
- `ReservationAccessTest`: mapeo de roles; principal = `upn` y no `sub` en tokens v1; Admin/Operador/Auditor ven todo, Cliente solo lo suyo.
- `HuespedResolverTest`: el Cliente toma nombre/email del token; Admin sin email o con email invalido -> 400.
- `ReservationControllerSecurityTest`: 200/403 por rol en cada endpoint; POST de Cliente usa los datos del token; Admin sin email -> 400 con mensaje.
- `RabbitReservationNotificationPublisherTest`: exchanges/keys por estado y tolerancia a broker caido.
