package com.andesstay.ms_andesstay_reservations.events;

import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;
import com.andesstay.ms_andesstay_reservations.service.ReservationEventPublisher;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Adaptador Kafka del puerto {@link ReservationEventPublisher}: publica {@link ReservationEvent}
 * en el tópico reservations.events.
 *
 * - Key = reservationId: los eventos de una reserva van a la misma partición y conservan su orden.
 * - El envío es asíncrono: la respuesta HTTP no espera la confirmación del broker.
 * - Si Kafka falla, se registra un warning y la reserva igual queda guardada (igual que con RabbitMQ).
 */
@Component
public class KafkaReservationEventPublisher implements ReservationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaReservationEventPublisher.class);

    private final KafkaTemplate<String, ReservationEvent> kafkaTemplate;
    private final String topic;
    private final String source;

    public KafkaReservationEventPublisher(KafkaTemplate<String, ReservationEvent> kafkaTemplate,
                                          KafkaTopicsProperties topics,
                                          @Value("${spring.application.name}") String source) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topics.reservationEvents().name();
        this.source = source;
    }

    @Override
    public void reservationCreated(ReservationEntity reserva, String actor) {
        publish(ReservationEventType.RESERVATION_CREATED, reserva, null, actor);
    }

    @Override
    public void statusChanged(ReservationEntity reserva, ReservationStatus estadoAnterior, String actor) {
        publish(ReservationEventType.RESERVATION_STATUS_CHANGED, reserva, estadoAnterior, actor);
    }

    @Override
    public void reservationUpdated(ReservationEntity reserva, String actor) {
        publish(ReservationEventType.RESERVATION_UPDATED, reserva, reserva.getEstado(), actor);
    }

    @Override
    public void reservationDeleted(ReservationEntity reserva, String actor) {
        publish(ReservationEventType.RESERVATION_DELETED, reserva, reserva.getEstado(), actor);
    }

    private void publish(ReservationEventType type, ReservationEntity reserva,
                         ReservationStatus estadoAnterior, String actor) {
        ReservationEvent event;
        try {
            event = toEvent(type, reserva, estadoAnterior, actor);
        } catch (RuntimeException ex) {
            // Evento inválido (falta un dato obligatorio): no se publica, pero no rompe la operación
            log.warn("[KAFKA-PUBLISH-SKIP] evento inválido type={} reservationId={}: {}",
                    type, reserva.getId(), ex.getMessage());
            return;
        }

        try {
            kafkaTemplate.send(topic, event.partitionKey(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("[KAFKA-PUBLISH-FAIL] type={} reservationId={} eventId={} topic={}: {}",
                                    event.eventType(), event.reservationId(), event.eventId(), topic, ex.getMessage());
                            return;
                        }
                        RecordMetadata md = result.getRecordMetadata();
                        log.info("[KAFKA-PUBLISH] type={} reservationId={} eventId={} topic={} partition={} offset={}",
                                event.eventType(), event.reservationId(), event.eventId(),
                                md.topic(), md.partition(), md.offset());
                    });
        } catch (RuntimeException ex) {
            // Kafka caído al pedir metadatos (max.block.ms) u otro error inmediato del productor
            log.warn("[KAFKA-PUBLISH-FAIL] type={} reservationId={} eventId={} topic={}: {}",
                    event.eventType(), event.reservationId(), event.eventId(), topic, ex.getMessage());
        }
    }

    /** Traduce la entidad JPA al DTO del evento: los consumidores nunca ven la entidad. */
    private ReservationEvent toEvent(ReservationEventType type, ReservationEntity reserva,
                                     ReservationStatus estadoAnterior, String actor) {
        return new ReservationEvent(
                UUID.randomUUID().toString(),
                type,
                Instant.now(),
                reserva.getId(),
                estadoAnterior == null ? null : estadoAnterior.name(),
                reserva.getEstado() == null ? null : reserva.getEstado().name(),
                actor,
                reserva.getAlojamientoNombre(),
                reserva.getUnidadId(),
                reserva.getFechaInicio(),
                reserva.getFechaFin(),
                reserva.getPrecioTotal() == null ? null : BigDecimal.valueOf(reserva.getPrecioTotal()),
                source);
    }
}
