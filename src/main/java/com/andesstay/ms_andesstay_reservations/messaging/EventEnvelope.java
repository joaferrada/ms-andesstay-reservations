package com.andesstay.ms_andesstay_reservations.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Sobre comun de todos los mensajes de AndesStay.
 *
 * @param type          tipo de comando (ej. EMAIL_RESERVA_CONFIRMADA)
 * @param eventId       identificador unico; tambien va como messageId AMQP y se usa para idempotencia
 * @param timestamp     instante de emision
 * @param traceId       agrupa todos los mensajes emitidos por una misma operacion
 * @param correlationId vincula el mensaje con la entidad de negocio (reservation-{id})
 * @param source        microservicio emisor
 * @param payload       datos del comando
 */
public record EventEnvelope<T>(
        String type,
        UUID eventId,
        Instant timestamp,
        String traceId,
        String correlationId,
        String source,
        T payload) {

    public static <T> EventEnvelope<T> of(String type, String traceId, String correlationId, String source, T payload) {
        return new EventEnvelope<>(type, UUID.randomUUID(), Instant.now(), traceId, correlationId, source, payload);
    }
}
