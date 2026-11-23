package com.andesstay.ms_andesstay_reservations.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Evento de dominio publicado en el tópico reservations.events.
 *
 * Describe un hecho que ya ocurrió con una reserva. Lo consumen:
 *  - ms-andesstay-audit  (timeline: quién hizo qué y cuándo)
 *  - ms-andesstay-report (KPIs: reservas por hora, tiempo de ciclo, unidades más demandadas)
 *
 * La key del mensaje en Kafka es reservationId: todos los eventos de una misma
 * reserva caen en la misma partición y se leen en el orden en que ocurrieron.
 *
 * @param eventId           UUID único del evento (idempotencia en los consumidores)
 * @param eventType         qué ocurrió
 * @param occurredAt        cuándo ocurrió (UTC)
 * @param reservationId     id de la reserva (también es la key del mensaje)
 * @param previousStatus    estado anterior; null en RESERVATION_CREATED
 * @param newStatus         estado después del evento
 * @param actor             usuario que ejecutó la acción (claim del JWT)
 * @param alojamientoNombre nombre del alojamiento reservado
 * @param unidadId          unidad del catálogo; puede ser null
 * @param fechaInicio       inicio de la estadía
 * @param fechaFin          fin de la estadía
 * @param precioTotal       monto total de la reserva
 * @param source            microservicio que publicó el evento
 */
public record ReservationEvent(
        String eventId,
        ReservationEventType eventType,
        Instant occurredAt,
        Long reservationId,
        String previousStatus,
        String newStatus,
        String actor,
        String alojamientoNombre,
        Long unidadId,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        BigDecimal precioTotal,
        String source
) {

    /** Validación mínima: un evento sin estos datos no sirve a ningún consumidor. */
    public ReservationEvent {
        Objects.requireNonNull(eventId, "eventId es obligatorio");
        Objects.requireNonNull(eventType, "eventType es obligatorio");
        Objects.requireNonNull(occurredAt, "occurredAt es obligatorio");
        Objects.requireNonNull(reservationId, "reservationId es obligatorio");
        Objects.requireNonNull(source, "source es obligatorio");
    }

    /** Key del mensaje en Kafka: garantiza el orden por reserva. */
    public String partitionKey() {
        return String.valueOf(reservationId);
    }
}
