package com.andesstay.ms_andesstay_reservations.messaging;

import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;
import com.andesstay.ms_andesstay_reservations.service.ReservationNotificationPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

/**
 * Adaptador RabbitMQ del puerto {@link ReservationNotificationPublisher}.
 *
 * Por cada cambio de estado emite los comandos definidos en {@link CommandType#planFor}.
 * Si el broker no esta disponible se registra un warning y la operacion de negocio continua.
 */
@Component
public class RabbitReservationNotificationPublisher implements ReservationNotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitReservationNotificationPublisher.class);
    static final String HEADER_TRACE_ID = "x-trace-id";
    private static final String CORRELATION_PREFIX = "reservation-";

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties props;

    public RabbitReservationNotificationPublisher(RabbitTemplate rabbitTemplate, MessagingProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    @Override
    public void publishStatusChanged(ReservationEntity reserva, ReservationStatus estadoAnterior) {
        String traceId = UUID.randomUUID().toString();
        String correlationId = CORRELATION_PREFIX + reserva.getId();
        ReservationPayload payload = ReservationPayload.from(reserva, estadoAnterior);

        for (CommandType command : CommandType.planFor(reserva.getEstado())) {
            EventEnvelope<ReservationPayload> envelope =
                    EventEnvelope.of(command.name(), traceId, correlationId, props.source(), payload);
            send(command.destination(), envelope);
        }
    }

    private void send(CommandType.Destination destination, EventEnvelope<?> envelope) {
        Target target = resolve(destination);
        String eventId = envelope.eventId().toString();
        try {
            rabbitTemplate.convertAndSend(target.exchange(), target.routingKey(), envelope, message -> {
                MessageProperties mp = message.getMessageProperties();
                mp.setMessageId(eventId);
                mp.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                mp.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                mp.setType(envelope.type());
                mp.setAppId(envelope.source());
                mp.setCorrelationId(envelope.correlationId());
                mp.setTimestamp(Date.from(envelope.timestamp()));
                mp.setHeader(HEADER_TRACE_ID, envelope.traceId());
                return message;
            }, new CorrelationData(eventId));
            log.info("[PUBLISH] type={} eventId={} exchange={} routingKey={} correlationId={}",
                    envelope.type(), eventId, target.exchange(), target.routingKey(), envelope.correlationId());
        } catch (RuntimeException ex) {
            // RabbitMQ caido o error de red: la reserva ya fue guardada, solo se pierde la notificacion.
            log.warn("[PUBLISH-FAIL] no se pudo publicar type={} eventId={} exchange={} routingKey={}: {}",
                    envelope.type(), eventId, target.exchange(), target.routingKey(), ex.getMessage());
        }
    }

    Target resolve(CommandType.Destination destination) {
        MessagingProperties.Exchanges ex = props.exchanges();
        return switch (destination) {
            case EMAIL -> new Target(ex.direct(), props.routes().email().directKey());
            case VOUCHER -> new Target(ex.direct(), props.routes().voucher().directKey());
            case HOUSEKEEPING_PREPARATION -> new Target(ex.topic(), props.publishing().housekeepingPreparationKey());
            case HOUSEKEEPING_CLEANING -> new Target(ex.topic(), props.publishing().housekeepingCleaningKey());
        };
    }

    record Target(String exchange, String routingKey) {
    }
}
